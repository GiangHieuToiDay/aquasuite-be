package com.aquasuite.ims.module.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Provisional SC-01/02 contract, shared with the future UC-36 implementation. */
public interface ProjectReservationApi {
    List<Reservation> allocate(Allocation command);
    List<Reservation> getReservations(UUID orderId);
    List<ReservationActivity> getHistory(UUID orderId);

    enum HoldType { TEMPORARY, DEPOSIT_BACKED }

    /** orderedQty is the reservation target for this selected line, not necessarily the whole order quantity. */
    record Line(UUID orderItemId, UUID variantId, BigDecimal orderedQty) {}

    record Allocation(UUID requestId, UUID orderId, String warehouseCode, HoldType type,
                      Instant temporaryExpiresAt, List<Line> lines, UUID actorId) {
        public Allocation { lines = List.copyOf(lines); }
    }

    record Reservation(UUID reservationId, UUID orderId, UUID orderItemId, UUID variantId,
                       String warehouseCode, BigDecimal heldQty, BigDecimal consumedQty,
                       BigDecimal releasedQty, HoldType type, Instant expiresAt) {}

    record ReservationActivity(UUID reservationId, Instant occurredAt, UUID actorId,
                               UUID requestId, String action, BigDecimal heldBefore, BigDecimal heldAfter) {}
}
