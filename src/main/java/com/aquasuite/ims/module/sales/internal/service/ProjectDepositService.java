package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi;
import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi.*;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.ProjectReservationApi;
import com.aquasuite.ims.module.inventory.api.ProjectReservationApi.*;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.error.ProjectDepositErrorCode;
import com.aquasuite.ims.module.sales.internal.payload.request.ProjectDepositRequests.*;
import com.aquasuite.ims.module.sales.internal.payload.response.ProjectDepositView;
import com.aquasuite.ims.module.sales.internal.payload.response.ProjectDepositView.*;
import com.aquasuite.ims.module.sales.internal.repository.ProjectDepositOrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.ProjectDepositOrderRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import com.aquasuite.ims.module.sales.internal.dev.DemoProjectReservationOrderFactory;
import java.time.*;
import java.util.*;

/**
 * UC-29 prototype. Serializes one JVM's demo workspace; never writes Order/payment/stock tables.
 * Finance and inventory remain behind public contracts. Replace demo adapters before production.
 */
@Service
@Profile("uc29-demo & !prod & !production")
public class ProjectDepositService {
    private final ProjectDepositOrderRepository orders;
    private final ProjectDepositOrderItemRepository items;
    private final ProjectDepositPaymentApi finance;
    private final ProjectReservationApi inventory;
    private final IamApi iam;
    private final Clock clock;
    private static final BigDecimal MINIMUM_PERCENT = new BigDecimal("20");
    private final DemoProjectReservationOrderFactory orderFactory;
    private final Map<UUID, Workspace> workspaces = new HashMap<>();
    private final Map<UUID, Operation> operations = new HashMap<>();

    public ProjectDepositService(ProjectDepositOrderRepository orders, ProjectDepositOrderItemRepository items,
                                 ProjectDepositPaymentApi finance, ProjectReservationApi inventory, IamApi iam,
                                 @Qualifier("projectDepositClock") Clock clock,
                                 DemoProjectReservationOrderFactory orderFactory) {
        this.orders = orders;
        this.items = items;
        this.finance = finance;
        this.inventory = inventory;
        this.iam = iam;
        this.clock = clock;
        this.orderFactory = orderFactory;
    }

    public synchronized ProjectDepositView get(UUID orderId) {
        Order order = requireOrder(orderId);
        return view(order, workspace(orderId));
    }

    public synchronized ProjectDepositView updateSchedule(UUID orderId, Schedule request) {
        Order order = requireOrder(orderId);
        Workspace state = workspace(orderId);
        if (request.expectedVersion() != state.version) throw error(ProjectDepositErrorCode.STALE_VERSION);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        if (request.deliveries() == null || request.deliveries().size() > 24
                || request.deliveries().stream().anyMatch(d -> d == null || d.date() == null || d.date().isBefore(today))) {
            throw error(ProjectDepositErrorCode.INVALID_REQUEST);
        }
        state.deliveries = List.copyOf(request.deliveries());
        state.version++;
        state.history.add(activity("DELIVERY_SCHEDULE_UPDATED", null));
        return view(order, state);
    }

    public synchronized ProjectDepositView requestReservation(UUID orderId, Selection request) {
        Order order = requireOrder(orderId);
        Workspace state = workspace(orderId);
        List<OrderItem> source = orderLines(orderId);
        List<RequestedLine> selected = normalizeSelection(request.lines(), source);
        Operation operation = operation(new Input(orderId, "SELECT", request.warehouseCode(), null, null, null, null, selected), request.requestId());
        if (operation.applied) return view(order, state);
        if (!state.selection.isEmpty() && !state.selection.equals(selected)) {
            throw error(ProjectDepositErrorCode.RESERVATION_SELECTION_CONFLICT);
        }
        if (!state.selection.isEmpty() && !Objects.equals(state.pricing, pricing(order, source))) {
            throw error(ProjectDepositErrorCode.RESERVATION_SELECTION_CONFLICT);
        }
        for (RequestedLine line : selected) {
            BigDecimal committed = inventory.getReservations(orderId).stream().filter(r -> r.orderItemId().equals(line.orderItemId()))
                    .map(r -> r.heldQty().add(r.consumedQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (committed.compareTo(line.quantity()) > 0) throw error(ProjectDepositErrorCode.RESERVATION_SELECTION_CONFLICT);
        }
        BigDecimal value = reservationValue(order, source, selected);
        state.selection = selected;
        state.reservationValue = value;
        state.pricing = pricing(order, source);
        state.syncStatus = qualifies(order) ? "RESERVATION_PENDING" : "AWAITING_DEPOSIT";
        operation.applied = true;
        state.history.add(activity("RESERVATION_REQUESTED", request.requestId()));
        // Register intent only: UC-29 no longer creates a free temporary hold before deposit.
        return view(order, state);
    }

    public synchronized ProjectDepositView createAndDeposit(CreateOrderDeposit request) {
        UserSummaryDto actor = requireSalesUser();
        Deposit deposit = new Deposit(request.requestId(), request.amount(), request.method(), request.referenceNo(),
                request.proofUrl(), request.warehouseCode());
        validateDeposit(deposit);
        if (operations.containsKey(request.requestId()) && !orderFactory.hasRequest(request.requestId())) {
            throw error(ProjectDepositErrorCode.IDEMPOTENCY_CONFLICT);
        }
        var created = orderFactory.create(request, actor.id());
        UUID orderId = created.order().getOrderId();
        UUID selectionKey = UUID.nameUUIDFromBytes(("uc29-selection:"+request.requestId()).getBytes(StandardCharsets.UTF_8));
        requestReservation(orderId, new Selection(selectionKey, request.warehouseCode(), created.items().stream()
                .map(i -> new RequestedLine(i.getOrderItemId(), i.getQuantity())).toList()));
        return deposit(orderId, deposit);
    }

    public synchronized ProjectDepositView deposit(UUID orderId, Deposit request) {
        Order order = requireOrder(orderId);
        requireSelection(order);
        validateDeposit(request);
        Input input = new Input(orderId, "DEPOSIT", request.warehouseCode(), request.amount().stripTrailingZeros(),
                request.method(), request.referenceNo(), request.proofUrl());
        Operation operation = operation(input, request.requestId());
        Workspace state = workspace(orderId);
        if (operation.applied) return view(order, state);
        if (operation.paymentId == null) {
            if (!operation.paymentAttempted) {
                List<Payment> knownPayments = payments(orderId);
                BigDecimal committed = knownPayments.stream().filter(p -> p.status() != Status.REJECTED)
                        .map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                // A timed-out payment may still commit externally: reserve that uncertain amount too.
                BigDecimal uncertain = operations.values().stream().filter(o -> o.input.orderId.equals(orderId)
                                && o.input.action.equals("DEPOSIT") && o.paymentAttempted && !o.paymentRejected && o.paymentId == null
                                && knownPayments.stream().noneMatch(p -> p.requestId().equals(o.requestId)))
                        .map(o -> o.input.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                committed = committed.add(uncertain);
                if (committed.add(request.amount()).compareTo(order.getTotalAmount()) > 0) {
                    operations.remove(request.requestId());
                    throw error(ProjectDepositErrorCode.INVALID_REQUEST);
                }
            }
            operation.paymentAttempted = true;
            try {
                Payment payment = finance.recordDeposit(new DepositCommand(request.requestId(), orderId,
                        order.getCustomerId(), operation.input.amount, request.method(), request.referenceNo(),
                        request.proofUrl(), operation.actorId));
                if (payment == null || !request.requestId().equals(payment.requestId()) || !orderId.equals(payment.orderId()) || payment.paymentId() == null
                        || payment.amount().compareTo(request.amount()) != 0) {
                    throw new IllegalStateException("Invalid payment integration result");
                }
                operation.paymentId = payment.paymentId();
                state.history.add(activity("DEPOSIT_RECORDED", request.requestId()));
            } catch (AppException rejected) {
                operation.paymentRejected = true;
                state.syncStatus = "PAYMENT_REJECTED";
                throw rejected;
            } catch (RuntimeException failure) {
                state.syncStatus = "PAYMENT_PENDING";
                throw error(ProjectDepositErrorCode.INTEGRATION_UNAVAILABLE);
            }
        }
        if (qualifies(order)) {
            allocate(order, state, operation);
        } else {
            state.syncStatus = verified(orderId).signum() == 0 ? "WAITING_VERIFICATION" : "BELOW_REQUIRED_DEPOSIT";
        }
        return view(order, state);
    }

    public synchronized ProjectDepositView synchronizeReservations(UUID orderId, Hold request) {
        Order order = requireOrder(orderId);
        requireSelection(order);
        Workspace state = workspace(orderId);
        Operation operation = operation(new Input(orderId, "SYNC", request.warehouseCode(), null, null, null, null), request.requestId());
        if (!operation.applied) {
            if (!qualifies(order)) {
                state.syncStatus = verified(orderId).signum() == 0 ? "WAITING_VERIFICATION" : "BELOW_REQUIRED_DEPOSIT";
            } else allocate(order, state, operation);
        }
        return view(order, state);
    }

    private void allocate(Order order, Workspace state, Operation operation) {
        try {
            requireSelection(order);
            Map<UUID, OrderItem> source = new HashMap<>();
            orderLines(order.getOrderId()).forEach(i -> source.put(i.getOrderItemId(), i));
            List<Line> lines = state.selection.stream().map(i -> new Line(i.orderItemId(),
                    source.get(i.orderItemId()).getVariantId(), i.quantity())).toList();
            inventory.allocate(new Allocation(operation.requestId, order.getOrderId(), operation.input.warehouseCode,
                    HoldType.DEPOSIT_BACKED, null, lines, operation.actorId));
            operation.applied = true;
            state.syncStatus = "APPLIED";
            state.history.add(activity("DEPOSIT_RESERVATION_SYNCHRONIZED", operation.requestId));
        } catch (RuntimeException failure) {
            state.syncStatus = "RESERVATION_PENDING";
            // Finance may already be verified: retain operation and allow explicit idempotent retry.
        }
    }

    private UserSummaryDto requireSalesUser() {
        UserSummaryDto user = iam.getCurrentUser();
        if (user == null || user.id() == null || user.roles() == null || user.roles().stream()
                .noneMatch(Set.of("SHOP_OWNER", "ADMIN", "SUPER_ADMIN", "SALES_STAFF")::contains)) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return user;
    }

    private Order requireOrder(UUID orderId) {
        UserSummaryDto user = requireSalesUser();
        boolean owner = user.roles().stream().anyMatch(Set.of("SHOP_OWNER", "ADMIN", "SUPER_ADMIN")::contains);
        Order order = orderFactory.findOrder(orderId).orElseGet(() -> orders.findById(orderId)
                .orElseThrow(() -> error(ProjectDepositErrorCode.ORDER_NOT_FOUND)));
        if (!owner && !Objects.equals(order.getSalesStaffId(), user.id())) throw new AppException(CommonErrorCode.ACCESS_DENIED);
        // The hold is optional for every customer; projectId is not an eligibility condition.
        if (order.getOrderStatus() == null || !Set.of("CONFIRMED", "PROCESSING").contains(order.getOrderStatus())
                || order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            throw error(ProjectDepositErrorCode.ORDER_NOT_ELIGIBLE);
        }
        return order;
    }

    private List<OrderItem> orderLines(UUID orderId) {
        List<OrderItem> result = orderFactory.findItems(orderId).orElseGet(() -> items.findByOrderIdOrderByOrderItemId(orderId));
        if (result.isEmpty() || result.stream().anyMatch(i -> i.getVariantId() == null || i.getQuantity() == null
                || i.getQuantity().signum() <= 0)) throw error(ProjectDepositErrorCode.ORDER_NOT_ELIGIBLE);
        return result;
    }

    private Operation operation(Input input, UUID requestId) {
        if (requestId == null || input.warehouseCode == null || !input.warehouseCode.matches("[A-Z0-9_-]{1,50}")) {
            throw error(ProjectDepositErrorCode.INVALID_REQUEST);
        }
        Operation existing = operations.get(requestId);
        if (existing != null) {
            if (!existing.input.equals(input)) throw error(ProjectDepositErrorCode.IDEMPOTENCY_CONFLICT);
            return existing;
        }
        Operation created = new Operation(requestId, input, clock.instant(), iam.getCurrentUserId());
        operations.put(requestId, created);
        return created;
    }

    private Workspace workspace(UUID orderId) { return workspaces.computeIfAbsent(orderId, ignored -> new Workspace()); }
    private BigDecimal required(Order order) {
        return workspace(order.getOrderId()).reservationValue.multiply(MINIMUM_PERCENT)
                .divide(new BigDecimal("100"), 2, RoundingMode.UP);
    }
    private boolean qualifies(Order order) {
        return !workspace(order.getOrderId()).selection.isEmpty() && verified(order.getOrderId()).compareTo(required(order)) >= 0;
    }

    private void validateDeposit(Deposit request) {
        if (request.requestId() == null || request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2
                || request.amount().precision() - request.amount().scale() > 13 || request.method() == null
                || request.warehouseCode() == null || !request.warehouseCode().matches("[A-Z0-9_-]{1,50}")
                || (request.method() == Method.BANK_TRANSFER && (request.referenceNo() == null || request.referenceNo().isBlank()))) {
            throw error(ProjectDepositErrorCode.INVALID_REQUEST);
        }
    }

    private void requireSelection(Order order) {
        Workspace state = workspace(order.getOrderId());
        if (state.selection.isEmpty()) throw error(ProjectDepositErrorCode.RESERVATION_REQUEST_REQUIRED);
        if (!Objects.equals(state.pricing, pricing(order, orderLines(order.getOrderId())))) {
            throw error(ProjectDepositErrorCode.RESERVATION_SELECTION_CONFLICT);
        }
    }

    private List<RequestedLine> normalizeSelection(List<RequestedLine> input, List<OrderItem> source) {
        if (input == null || input.isEmpty() || input.size() > 200) throw error(ProjectDepositErrorCode.INVALID_REQUEST);
        Map<UUID, OrderItem> byId = new HashMap<>();
        source.forEach(i -> byId.put(i.getOrderItemId(), i));
        Set<UUID> seen = new HashSet<>();
        List<RequestedLine> result = new ArrayList<>();
        for (RequestedLine line : input) {
            if (line == null || line.orderItemId() == null || !seen.add(line.orderItemId()) || !byId.containsKey(line.orderItemId())
                    || line.quantity() == null || line.quantity().signum() <= 0 || line.quantity().scale() > 2
                    || line.quantity().compareTo(byId.get(line.orderItemId()).getQuantity()) > 0) throw error(ProjectDepositErrorCode.INVALID_REQUEST);
            result.add(new RequestedLine(line.orderItemId(), line.quantity().stripTrailingZeros()));
        }
        result.sort(Comparator.comparing(RequestedLine::orderItemId));
        return List.copyOf(result);
    }

    private BigDecimal reservationValue(Order order, List<OrderItem> source, List<RequestedLine> selection) {
        Map<UUID, BigDecimal> quantities = new HashMap<>();
        selection.forEach(l -> quantities.put(l.orderItemId(), l.quantity()));
        BigDecimal allLines = BigDecimal.ZERO, selectedLines = BigDecimal.ZERO;
        for (OrderItem line : source) {
            if (line.getLineTotal() == null || line.getLineTotal().signum() < 0) throw error(ProjectDepositErrorCode.ORDER_NOT_ELIGIBLE);
            allLines = allLines.add(line.getLineTotal());
            BigDecimal qty = quantities.getOrDefault(line.getOrderItemId(), BigDecimal.ZERO);
            selectedLines = selectedLines.add(line.getLineTotal().multiply(qty)
                    .divide(line.getQuantity(), MathContext.DECIMAL128));
        }
        if (allLines.signum() <= 0 || selectedLines.signum() <= 0) throw error(ProjectDepositErrorCode.INVALID_REQUEST);
        // Pro-rate the agreed final total so order-level discounts are included and a full selection equals the order total.
        BigDecimal result = order.getTotalAmount().multiply(selectedLines).divide(allLines, 2, RoundingMode.HALF_UP);
        if (result.signum() <= 0) throw error(ProjectDepositErrorCode.INVALID_REQUEST);
        return result;
    }

    private Pricing pricing(Order order, List<OrderItem> source) {
        return new Pricing(order.getTotalAmount().stripTrailingZeros(), source.stream()
                .map(i -> new PricedLine(i.getOrderItemId(), i.getVariantId(), i.getQuantity().stripTrailingZeros(),
                        i.getLineTotal() == null ? null : i.getLineTotal().stripTrailingZeros()))
                .sorted(Comparator.comparing(PricedLine::id)).toList());
    }

    private BigDecimal verified(UUID orderId) {
        return payments(orderId).stream().filter(p -> p.status() == Status.VERIFIED)
                .map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private List<Payment> payments(UUID orderId) {
        try {
            List<Payment> result = List.copyOf(finance.getPayments(orderId));
            if (result.stream().anyMatch(p -> p.requestId() == null || !orderId.equals(p.orderId()) || p.status() == null
                    || p.amount() == null || p.amount().signum() <= 0)) throw new IllegalStateException("Invalid payment snapshot");
            return result;
        }
        catch (RuntimeException failure) { throw error(ProjectDepositErrorCode.INTEGRATION_UNAVAILABLE); }
    }

    private ProjectDepositView view(Order order, Workspace state) {
        if (!state.selection.isEmpty()) requireSelection(order);
        List<Payment> payments = payments(order.getOrderId());
        List<Reservation> reservations;
        List<ReservationActivity> reservationHistory;
        try {
            reservations = List.copyOf(inventory.getReservations(order.getOrderId()));
            reservationHistory = List.copyOf(inventory.getHistory(order.getOrderId()));
            if (reservations.stream().anyMatch(r -> !order.getOrderId().equals(r.orderId()))) {
                throw new IllegalStateException("Invalid reservation snapshot");
            }
        }
        catch (RuntimeException failure) { throw error(ProjectDepositErrorCode.INTEGRATION_UNAVAILABLE); }
        BigDecimal paid = payments.stream().filter(p -> p.status() == Status.VERIFIED).map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LineBalance> balances = orderLines(order.getOrderId()).stream().map(item -> {
            List<Reservation> matching = reservations.stream().filter(r -> r.orderItemId().equals(item.getOrderItemId())).toList();
            BigDecimal held = matching.stream().map(Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal consumed = matching.stream().map(Reservation::consumedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal requested = state.selection.stream().filter(l -> l.orderItemId().equals(item.getOrderItemId()))
                    .map(RequestedLine::quantity).findFirst().orElse(BigDecimal.ZERO);
            return new LineBalance(item.getOrderItemId(), item.getVariantId(), item.getQuantity(), requested, held, consumed,
                    requested.subtract(held).subtract(consumed).max(BigDecimal.ZERO));
        }).toList();
        String syncStatus = state.syncStatus;
        if (!state.selection.isEmpty() && paid.compareTo(required(order)) >= 0 && Set.of("AWAITING_DEPOSIT", "WAITING_VERIFICATION", "BELOW_REQUIRED_DEPOSIT").contains(syncStatus)) {
            syncStatus = "RESERVATION_PENDING";
        }
        return new ProjectDepositView(order.getOrderId(), true, order.getTotalAmount(), !state.selection.isEmpty(), state.reservationValue, MINIMUM_PERCENT, required(order), paid,
                order.getTotalAmount().subtract(paid).max(BigDecimal.ZERO), !state.selection.isEmpty() && paid.compareTo(required(order)) >= 0,
                syncStatus, state.version, state.deliveries, payments, reservations, balances, state.history,
                reservationHistory);
    }

    private Activity activity(String action, UUID requestId) { return new Activity(clock.instant(), iam.getCurrentUserId(), action, requestId); }
    private static AppException error(ProjectDepositErrorCode code) { return new AppException(code); }
    private record Input(UUID orderId, String action, String warehouseCode, BigDecimal amount, Method method,
                         String referenceNo, String proofUrl, List<RequestedLine> selection) {
        Input(UUID orderId, String action, String warehouseCode, BigDecimal amount, Method method, String referenceNo, String proofUrl) {
            this(orderId, action, warehouseCode, amount, method, referenceNo, proofUrl, List.of());
        }
    }
    private record PricedLine(UUID id, UUID variantId, BigDecimal quantity, BigDecimal lineTotal) {}
    private record Pricing(BigDecimal total, List<PricedLine> lines) {}
    private static final class Operation {
        final UUID requestId;
        final Input input;
        final Instant createdAt;
        final UUID actorId;
        UUID paymentId;
        boolean paymentAttempted;
        boolean paymentRejected;
        boolean applied;
        Operation(UUID requestId, Input input, Instant createdAt, UUID actorId) {
            this.requestId = requestId; this.input = input; this.createdAt = createdAt; this.actorId = actorId;
        }
    }
    private static final class Workspace {
        List<RequestedLine> selection = List.of();
        BigDecimal reservationValue = BigDecimal.ZERO;
        Pricing pricing;
        long version;
        List<DeliveryDate> deliveries = List.of();
        final List<Activity> history = new ArrayList<>();
        String syncStatus = "NOT_REQUESTED";
    }
}
