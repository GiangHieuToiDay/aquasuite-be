package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.*;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequestItem;
import com.aquasuite.ims.module.sales.internal.enums.*;
import com.aquasuite.ims.module.sales.internal.payload.response.ReservationManagementView;
import com.aquasuite.ims.module.sales.internal.payload.response.ReservationManagementView.*;
import com.aquasuite.ims.module.sales.internal.repository.*;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.aquasuite.ims.module.sales.internal.error.ProjectDepositErrorCode.ORDER_NOT_FOUND;
import static com.aquasuite.ims.module.sales.internal.error.ReservationManagementErrorCode.*;

@Service
@RequiredArgsConstructor
public class ReservationManagementService {
    private final OrderRepository orders;
    private final OrderModificationRequestRepository decisions;
    private final OrderModificationRequestItemRepository items;
    private final ReservationReleaseStore releases;
    private final StockReservationApi inventory;
    private final IamApi iam;
    private final ReservationActorNames actorNames;

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
        var lines = items.findByModificationRequestIdOrderByCreatedAtAsc(d.getModificationRequestId());
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
}
