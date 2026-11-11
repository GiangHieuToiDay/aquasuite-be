package com.aquasuite.ims.module.inventory.api;

import com.aquasuite.ims.module.inventory.api.dto.SkuReservationStock;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistent reservation capability. UC29 callers validate orders, funding and authority.
 * UC36 exposes a staff read view, with no direct HTTP reservation-write bypass.
 * Quantities are a fixed cumulative source-line target shared across warehouses.
 */
public interface StockReservationApi {
    Reservation reserve(Reserve command);
    /** UC29 entry point: rejects SKU balances with unowned legacy holds before allocation. */
    Reservation reserveForDeposit(Reserve command);
    /** Only for orders whose persisted intent deferred every creation-time hold.
     * Existing aggregate holds from other workflows remain excluded from availability.
     */
    Reservation reserveDeferredDeposit(Reserve command);
    Reservation release(Release command);
    List<Reservation> findByOrder(UUID orderId);
    List<Activity> history(UUID reservationId);
    /** Quantity buckets for the SKU in each warehouse; held units are excluded from availability. */
    List<SkuReservationStock> stockByVariant(UUID variantId);

    record Reserve(UUID requestId, UUID orderId, UUID orderItemId, UUID variantId,
                   String warehouseCode, BigDecimal requestedQty, UUID actorId) {}
    record Release(UUID requestId, UUID reservationId, BigDecimal quantity, String reason, UUID actorId) {}
    record Reservation(UUID reservationId, UUID orderId, UUID orderItemId, UUID variantId,
                       String warehouseCode, BigDecimal requestedQty, BigDecimal heldQty,
                       BigDecimal consumedQty, BigDecimal releasedQty, BigDecimal waitingQty) {
        /** Status of this reservation's current quantity; the rest of the SKU stays available. */
        @JsonProperty(value = "status", access = JsonProperty.Access.READ_ONLY)
        public String status() {
            if (heldQty.signum() > 0) return "HELD";
            if (waitingQty.signum() > 0) return "WAITING";
            if (consumedQty.signum() > 0) return "CONSUMED";
            return "RELEASED";
        }
    }
    record Activity(UUID requestId, UUID reservationId, String action, BigDecimal heldBefore,
                    BigDecimal heldAfter, UUID actorId, String reason, Instant createdAt) {}
}
