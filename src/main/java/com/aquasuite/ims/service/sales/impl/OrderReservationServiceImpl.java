package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.dto.finance.response.OrderReceivableView;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest.Line;
import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest;
import com.aquasuite.ims.dto.sales.response.OrderStockReservationView;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView.*;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.entity.sales.OrderModificationRequest;
import com.aquasuite.ims.entity.sales.OrderModificationRequestItem;
import com.aquasuite.ims.enums.sales.*;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.sales.ProjectDepositErrorCode;
import com.aquasuite.ims.repository.sales.*;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.store.*;
import com.aquasuite.ims.repository.sales.store.OrderStockReservationStore;
import com.aquasuite.ims.service.finance.PaymentService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.StockReservationService.*;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.service.sales.OrderReservationService;
import com.aquasuite.ims.service.sales.helper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.aquasuite.ims.exception.sales.ProjectDepositErrorCode.*;
import static com.aquasuite.ims.exception.sales.ReservationManagementErrorCode.*;

@Service
@RequiredArgsConstructor
public class OrderReservationServiceImpl implements OrderReservationService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStockReservationStore store;
    private final PaymentService finance;
    private final StockReservationService inventory;
    private final IamQueryService iam;
    private final ObjectMapper json;
    private final OrderModificationRequestRepository decisions;
    private final OrderModificationRequestItemRepository modificationItems;
    private final ReservationReleaseStore releases;
    private final ReservationActorNames actorNames;

    /** Conversion already authorized the order; register its intent without touching money or stock. */
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public void registerConvertedOrder(Order order, List<OrderItem> source, UUID actorId) {
        if (actorId == null) throw new AppException(CommonErrorCode.ACCESS_DENIED);
        eligible(order);
        List<Line> lines = normalize(source.stream()
                .map(item -> new Line(item.getOrderItemId(), item.getQuantity())).toList(), source);
        // JDBC selection must see the new JPA order/items in this same transaction.
        orders.flush();
        items.flush();
        store.select(order.getOrderId(), encode(lines), pricing(order,source), value(order,source,lines), actorId);
        store.warehouse(order.getOrderId(), "WH_MAIN");
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public OrderStockReservationView get(UUID orderId) {
        Order order = requireOrder(orderId,actor());
        var selected = store.selection(orderId);
        if (selected.isPresent()) checkPricing(order,source(orderId),selected.get());
        return view(order,selected,payment(order));
    }

    @Override
    @Transactional
    public OrderStockReservationView apply(UUID orderId, OrderStockReservationRequest request, boolean synchronize) {
        UserSummaryDto actor = actor();
        validate(request);
        // Request first, then order; avoids cross-order reuse of a key racing the unique constraint.
        store.lockRequest(request.requestId());
        store.lockOrder(orderId);
        Order order = requireOrder(orderId,actor);
        List<OrderItem> source = source(orderId);
        List<Line> lines = normalize(request.lines(),source);
        String payload = encode(List.of(orderId,synchronize ? "SYNC" : "REQUEST",request.warehouseCode(),lines));
        Optional<String> prior = store.operation(request.requestId());
        if (prior.isPresent() && !prior.get().equals(payload)) fail(IDEMPOTENCY_CONFLICT);
        var selected = store.selection(orderId);
        if (selected.isEmpty()) {
            if (synchronize) fail(RESERVATION_REQUEST_REQUIRED);
            // Existing reservations from another workflow require explicit reconciliation, not adoption.
            if (!inventory.findByOrder(orderId).isEmpty()) fail(RESERVATION_SELECTION_CONFLICT);
            store.select(orderId,encode(lines),pricing(order,source),value(order,source,lines),actor.id());
            store.warehouse(orderId,request.warehouseCode());
            selected = store.selection(orderId);
        }
        if (!selected.orElseThrow().lines().equals(encode(lines))) fail(RESERVATION_SELECTION_CONFLICT);
        if (selected.get().warehouseCode() == null) {
            store.warehouse(orderId,request.warehouseCode());
            selected = store.selection(orderId);
        }
        if (!Objects.equals(selected.orElseThrow().warehouseCode(),request.warehouseCode())) fail(RESERVATION_SELECTION_CONFLICT);
        checkPricing(order,source,selected.get());
        OrderReceivableView payment = payment(order);
        if (payment.depositSatisfied()) {
            allocate(orderId,source,lines,request.requestId(),request.warehouseCode(),actor.id(),order.isReserveStockAfterDeposit());
            store.clearFailures(orderId);
        }
        if (prior.isEmpty()) store.record(request.requestId(),orderId,payload,actor.id());
        return view(order,selected,payment);
    }

    /** Trusted internal worker only; authority and actor come from the saved staff confirmation. */
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public void allocateAutomatically(UUID orderId, UUID operationId) {
        store.lockOrder(orderId);
        var selected = store.selection(orderId);
        if (selected.isEmpty()) return;
        Order order = orders.findById(orderId).orElseThrow(() -> new AppException(ORDER_NOT_FOUND));
        eligible(order);
        List<OrderItem> source = source(orderId);
        checkPricing(order,source,selected.get());
        if (selected.get().warehouseCode() == null || selected.get().actorId() == null) fail(RESERVATION_SELECTION_CONFLICT);
        List<Line> lines = normalize(decode(selected.get().lines()),source);
        if (!payment(order).depositSatisfied()) return;
        allocate(orderId,source,lines,operationId,selected.get().warehouseCode(),selected.get().actorId(),order.isReserveStockAfterDeposit());
        store.clearFailures(orderId);
    }

    private void allocate(UUID orderId,List<OrderItem> source,List<Line> lines,UUID operationId,String warehouse,UUID actorId,boolean deferred) {
        // UC36: an approved reduction/cancellation invalidates the old immutable UC29 target.
        if (store.requiresReservationReview(orderId)) fail(RESERVATION_SELECTION_CONFLICT);
        Map<UUID,OrderItem> byId = new HashMap<>();
        source.forEach(i -> byId.put(i.getOrderItemId(),i));
        List<Line> allocation = new ArrayList<>(lines);
        allocation.sort(Comparator.comparing((Line l) -> byId.get(l.orderItemId()).getVariantId()).thenComparing(Line::orderItemId));
        for (Line line : allocation) {
            UUID key = UUID.nameUUIDFromBytes(("uc29:"+operationId+":"+line.orderItemId()).getBytes(StandardCharsets.UTF_8));
            var command = new StockReservationService.Reserve(key,orderId,line.orderItemId(),
                    byId.get(line.orderItemId()).getVariantId(),warehouse,line.quantity(),actorId);
            if (deferred) inventory.reserveDeferredDeposit(command);
            else inventory.reserveForDeposit(command);
        }
    }

    private UserSummaryDto actor() {
        UserSummaryDto actor = iam.getCurrentUser();
        if (actor == null || actor.id() == null || actor.roles() == null || actor.roles().stream()
                .noneMatch(Set.of("SHOP_OWNER","ADMIN","SUPER_ADMIN","SALES_STAFF")::contains)) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return actor;
    }
    private Order requireOrder(UUID id,UserSummaryDto actor) {
        Order order = orders.findById(id).orElseThrow(() -> new AppException(ORDER_NOT_FOUND));
        boolean owner = actor.roles().stream().anyMatch(Set.of("SHOP_OWNER","ADMIN","SUPER_ADMIN")::contains);
        if (!owner && !actor.id().equals(order.getSalesStaffId())) throw new AppException(CommonErrorCode.ACCESS_DENIED);
        eligible(order);
        return order;
    }
    private void eligible(Order order) {
        if (!Set.of("CONFIRMED","PROCESSING").contains(Objects.toString(order.getOrderStatus(),""))
                || order.getCustomerId() == null || order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            fail(ORDER_NOT_ELIGIBLE);
        }
    }
    private List<OrderItem> source(UUID id) {
        List<OrderItem> source = items.findByOrderId(id);
        if (source.isEmpty() || source.stream().anyMatch(i -> i.getOrderItemId() == null || i.getVariantId() == null
                || i.getQuantity() == null || i.getQuantity().signum() < 0
                || i.getLineTotal() == null || i.getLineTotal().signum() < 0)) fail(ORDER_NOT_ELIGIBLE);
        // UC26 retains removed/replaced rows with quantity zero for history.
        // They are not part of the effective order or its reservation value.
        List<OrderItem> active = source.stream().filter(i -> i.getQuantity().signum() > 0).toList();
        if (active.isEmpty()) fail(ORDER_NOT_ELIGIBLE);
        return active;
    }
    private void validate(OrderStockReservationRequest r) {
        if (r == null || r.requestId() == null || r.warehouseCode() == null
                || !r.warehouseCode().matches("[A-Z0-9_-]{1,50}") || r.lines() == null
                || r.lines().isEmpty() || r.lines().size()>200) fail(INVALID_REQUEST);
    }
    private List<Line> normalize(List<Line> lines,List<OrderItem> source) {
        Map<UUID,OrderItem> byId = new HashMap<>();
        source.forEach(i -> byId.put(i.getOrderItemId(),i));
        Set<UUID> seen = new HashSet<>();
        List<Line> result = new ArrayList<>();
        for (Line l : lines) {
            if (l == null || l.orderItemId() == null || !seen.add(l.orderItemId()) || !byId.containsKey(l.orderItemId())
                    || l.quantity() == null || l.quantity().signum() <= 0 || l.quantity().stripTrailingZeros().scale()>2
                    || l.quantity().compareTo(new BigDecimal("9999999999.99"))>0
                    || l.quantity().compareTo(byId.get(l.orderItemId()).getQuantity())>0) fail(INVALID_REQUEST);
            result.add(new Line(l.orderItemId(),l.quantity().stripTrailingZeros()));
        }
        result.sort(Comparator.comparing(Line::orderItemId));
        return List.copyOf(result);
    }
    private String pricing(Order order,List<OrderItem> source) {
        return encode(List.of(order.getTotalAmount().stripTrailingZeros(),source.stream()
                .sorted(Comparator.comparing(OrderItem::getOrderItemId))
                .map(i -> List.of(i.getOrderItemId(),i.getVariantId(),i.getQuantity().stripTrailingZeros(),
                        i.getLineTotal().stripTrailingZeros())).toList()));
    }
    private void checkPricing(Order order,List<OrderItem> source,OrderStockReservationStore.Selection selected) {
        if (!selected.pricing().equals(pricing(order,source))) fail(RESERVATION_SELECTION_CONFLICT);
    }
    private BigDecimal value(Order order,List<OrderItem> source,List<Line> lines) {
        Map<UUID,BigDecimal> selected = new HashMap<>();
        lines.forEach(l -> selected.put(l.orderItemId(),l.quantity()));
        BigDecimal all=BigDecimal.ZERO,part=BigDecimal.ZERO;
        for (OrderItem i : source) {
            all=all.add(i.getLineTotal());
            part=part.add(i.getLineTotal().multiply(selected.getOrDefault(i.getOrderItemId(),BigDecimal.ZERO))
                    .divide(i.getQuantity(),MathContext.DECIMAL128));
        }
        if (all.signum()<=0 || part.signum()<=0) fail(INVALID_REQUEST);
        BigDecimal result=order.getTotalAmount().multiply(part).divide(all,2,RoundingMode.HALF_UP);
        if (result.signum()<=0) fail(INVALID_REQUEST);
        return result;
    }
    private OrderReceivableView payment(Order order) {
        return finance.getOrderReceivable(order.getOrderId())
                .orElseThrow(() -> new AppException(INTEGRATION_UNAVAILABLE));
    }
    private OrderStockReservationView view(Order order,Optional<OrderStockReservationStore.Selection> selected,OrderReceivableView payment) {
        BigDecimal value=selected.map(OrderStockReservationStore.Selection::value).orElse(BigDecimal.ZERO);
        boolean funded=payment.depositSatisfied();
        var reservations=inventory.findByOrder(order.getOrderId());
        String depositStatus=selected.isEmpty() ? "NOT_REQUESTED" : funded ? "SATISFIED"
                : payment.verifiedPaid().signum()>0 ? "PARTIALLY_PAID"
                : payment.pendingAmount().signum()>0 ? "PENDING_VERIFICATION" : "AWAITING_DEPOSIT";
        var job=store.latestAllocation(order.getOrderId());
        String allocationStatus=selected.isEmpty() ? "NOT_REQUESTED" : !funded ? "AWAITING_DEPOSIT"
                : job.map(a -> "FAILED".equals(a.status()) ? "FAILED" : "PENDING".equals(a.status()) ? "PENDING" : "COMPLETED")
                        .orElse(reservations.isEmpty() ? "PENDING" : "COMPLETED");
        return new OrderStockReservationView(order.getOrderId(),selected.isPresent(),value,payment,
                status(selected.isPresent(),funded,reservations),
                selected.map(s -> decode(s.lines())).orElse(List.of()),reservations,
                depositStatus,allocationStatus,
                job.filter(a -> "FAILED".equals(a.status())).map(OrderStockReservationStore.Allocation::errorCode).orElse(null),
                selected.map(OrderStockReservationStore.Selection::warehouseCode).orElse(null),
                job.map(OrderStockReservationStore.Allocation::allocationId).orElse(null),
                lineStatuses(selected.map(s -> decode(s.lines())).orElse(List.of()),funded,reservations));
    }
    private List<OrderStockReservationView.LineStatus> lineStatuses(List<Line> lines,boolean funded,
            List<StockReservationService.Reservation> reservations) {
        return lines.stream().map(line -> {
            var matching=reservations.stream().filter(r -> r.orderItemId().equals(line.orderItemId())).toList();
            BigDecimal held=matching.stream().map(StockReservationService.Reservation::heldQty).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal consumed=matching.stream().map(StockReservationService.Reservation::consumedQty).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal released=matching.stream().map(StockReservationService.Reservation::releasedQty).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal waiting=line.quantity().subtract(held).subtract(consumed).subtract(released).max(BigDecimal.ZERO);
            String state=!funded ? "AWAITING_DEPOSIT" : matching.isEmpty() ? "RESERVATION_PENDING"
                    : held.signum()>0 ? (waiting.signum()>0 ? "PARTIALLY_HELD" : "HELD")
                    : waiting.signum()>0 ? "WAITING_STOCK" : consumed.signum()>0 ? "CONSUMED" : "RELEASED";
            return new OrderStockReservationView.LineStatus(line.orderItemId(),line.quantity(),held,waiting,state);
        }).toList();
    }
    private String status(boolean requested, boolean funded, List<StockReservationService.Reservation> reservations) {
        if (!requested) return "NOT_REQUESTED";
        if (!funded) return "AWAITING_DEPOSIT";
        if (reservations.isEmpty()) return "RESERVATION_PENDING";
        boolean hasHeld = reservations.stream().anyMatch(r -> r.heldQty().signum() > 0);
        if (reservations.stream().anyMatch(r -> r.waitingQty().signum() > 0)) {
            return hasHeld ? "PARTIALLY_HELD" : "WAITING_STOCK";
        }
        if (hasHeld) return "HELD";
        return reservations.stream().anyMatch(r -> r.consumedQty().signum() > 0) ? "CONSUMED" : "RELEASED";
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode reservation request",e); }
    }
    private List<Line> decode(String value) {
        try { return json.readValue(value,new TypeReference<List<Line>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored reservation request",e); }
    }
    private void fail(ProjectDepositErrorCode code) { throw new AppException(code); }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReservationManagementView history(UUID orderId) {
        actor(false);
        if (!orders.existsById(orderId)) throw new AppException(ORDER_NOT_FOUND);
        var reservations = inventory.findByOrder(orderId);
        List<Activity> activities = reservations.stream().flatMap(r -> inventory.history(r.reservationId()).stream())
                .sorted(Comparator.comparing(Activity::createdAt).thenComparing(Activity::requestId)).toList();
        var requests = decisions.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(d -> {
            var result = releases.find(d.getModificationRequestId()).orElse(null);
            String state = result != null ? "COMPLETED"
                    : d.getRequestType() == OrderModificationRequestType.ADDRESS_CHANGE ? "NOT_APPLICABLE"
                    : d.getRequestStatus() == OrderModificationRequestStatus.REJECTED ? "NOT_APPLICABLE"
                    : d.getRequestStatus() == OrderModificationRequestStatus.PENDING_CANCEL ? "PENDING_UC26_COMPLETION"
                    : d.getRequestStatus() == OrderModificationRequestStatus.APPROVED ? "PENDING_RELEASE" : "PENDING_APPROVAL";
            return new Decision(d.getModificationRequestId(), d.getRequestType().name(), d.getRequestStatus().name(),
                    d.getRequestedBy(), d.getCreatedAt(), d.getCustomerReason(), d.getReviewedBy(), d.getReviewedAt(),
                    d.getDecisionNote(), state, result);
        }).toList();
        Set<UUID> actorIds = new HashSet<>();
        activities.forEach(activity -> actorIds.add(activity.actorId()));
        requests.forEach(request -> {
            actorIds.add(request.requestedBy());
            actorIds.add(request.reviewedBy());
            if (request.release() != null) actorIds.add(request.release().executedBy());
        });
        actorIds.remove(null);
        return new ReservationManagementView(orderId, reservations, activities, requests,
                actorNames.resolve(actorIds));
    }

    @Transactional
    public ReleaseResult release(UUID orderId, UUID decisionId) {
        var executor = actor(true);
        orders.findByIdForUpdate(orderId).orElseThrow(() -> new AppException(ORDER_NOT_FOUND));
        var decision = decisions.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new AppException(DECISION_NOT_FOUND));
        if (!orderId.equals(decision.getOrderId())) throw new AppException(INVALID_APPROVAL);
        var prior = releases.find(decisionId);
        if (prior.isPresent()) return prior.get();
        if (decision.getRequestStatus() != OrderModificationRequestStatus.APPROVED
                || decision.getReviewedBy() == null || decision.getReviewedAt() == null
                || !reviewedByOwner(decision.getReviewedBy())) {
            throw new AppException(INVALID_APPROVAL);
        }
        boolean full = decision.getRequestType() == OrderModificationRequestType.FULL_CANCELLATION;
        Map<UUID, OrderModificationRequestItem> reductions = reductions(decision, full);
        var reservations = inventory.findByOrder(orderId).stream()
                .sorted(Comparator.comparing(Reservation::variantId).thenComparing(Reservation::orderItemId)
                        .thenComparing(Reservation::reservationId)).toList();
        List<ReleasedLine> resultLines = new ArrayList<>();
        for (var r : reservations) {
            var line = reductions.get(r.orderItemId());
            if (!full && line == null) continue;
            if (!"WH_MAIN".equals(r.warehouseCode()) || r.consumedQty().signum() > 0
                    || (!full && (!r.variantId().equals(line.getOriginalVariantId())
                    || r.requestedQty().compareTo(line.getOriginalQuantity()) > 0))) {
                throw new AppException(REVIEW_REQUIRED);
            }
            BigDecimal retained = full || line.getChangeAction() == OrderModificationItemAction.REMOVE
                    ? BigDecimal.ZERO : line.getRequestedQuantity();
            // Keep enough held stock for the retained order quantity; waiting units are never released.
            BigDecimal quantity = r.heldQty().subtract(retained).max(BigDecimal.ZERO);
            if (quantity.signum() > 0) {
                UUID key = UUID.nameUUIDFromBytes(("uc36:" + decisionId + ":" + r.reservationId())
                        .getBytes(StandardCharsets.UTF_8));
                inventory.release(new Release(key, r.reservationId(), quantity,
                        "Approved UC26 decision " + decisionId, executor.id()));
            }
            resultLines.add(new ReleasedLine(r.reservationId(), r.orderItemId(), quantity));
        }
        var result = new ReleaseResult(decisionId, orderId, executor.id(), Instant.now(), List.copyOf(resultLines));
        releases.save(result);
        return result;
    }

    private Map<UUID, OrderModificationRequestItem> reductions(OrderModificationRequest d, boolean full) {
        if (full) return Map.of();
        if (d.getRequestType() != OrderModificationRequestType.ITEM_CHANGE) throw new AppException(REVIEW_REQUIRED);
        var lines = modificationItems.findByModificationRequestIdOrderByCreatedAtAsc(d.getModificationRequestId());
        if (lines.isEmpty()) throw new AppException(REVIEW_REQUIRED);
        Map<UUID, OrderModificationRequestItem> result = new HashMap<>();
        for (var l : lines) {
            if (l.getOrderItemId() == null || l.getOriginalVariantId() == null || l.getOriginalQuantity() == null
                    || l.getOriginalQuantity().signum() <= 0 || result.put(l.getOrderItemId(), l) != null
                    || l.getOriginalQuantity().stripTrailingZeros().scale() > 2
                    || (l.getChangeAction() == OrderModificationItemAction.REMOVE
                        && l.getRequestedQuantity() != null && l.getRequestedQuantity().signum() != 0)
                    || (l.getRequestedVariantId() != null && !l.getRequestedVariantId().equals(l.getOriginalVariantId()))
                    || (l.getChangeAction() != OrderModificationItemAction.REMOVE
                        && l.getChangeAction() != OrderModificationItemAction.REDUCE)
                    || (l.getChangeAction() == OrderModificationItemAction.REDUCE
                        && (l.getRequestedQuantity() == null || l.getRequestedQuantity().signum() < 0
                            || l.getRequestedQuantity().compareTo(l.getOriginalQuantity()) >= 0
                            || l.getRequestedQuantity().stripTrailingZeros().scale() > 2))) {
                throw new AppException(REVIEW_REQUIRED);
            }
        }
        return result;
    }

    private UserSummaryDto actor(boolean write) {
        var user = iam.getCurrentUser();
        if (user == null || user.id() == null || user.roles() == null
                || (write ? !hasRole(user, "SHOP_OWNER") : user.roles().stream().noneMatch(
                        Set.of("SHOP_OWNER", "ADMIN", "SUPER_ADMIN", "SALES_STAFF", "ACCOUNTANT")::contains))) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return user;
    }

    private boolean reviewedByOwner(UUID reviewer) {
        try { return hasRole(iam.getUserById(reviewer), "SHOP_OWNER"); }
        catch (AppException missing) {
            if (missing.getErrorCode() == CommonErrorCode.UNAUTHORIZED
                    || missing.getErrorCode() == CommonErrorCode.RESOURCE_NOT_FOUND) return false;
            throw missing;
        }
    }

    private boolean hasRole(UserSummaryDto user, String role) {
        return user != null && user.roles() != null && user.roles().contains(role);
    }

    @Override
    @Transactional
    public ReleaseResult release(UUID orderId, UUID requestId, String reason) {
        return release(orderId, requestId);
    }
}
