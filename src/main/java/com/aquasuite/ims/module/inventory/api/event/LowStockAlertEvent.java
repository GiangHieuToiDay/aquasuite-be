package com.aquasuite.ims.module.inventory.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LowStockAlertEvent(UUID eventId, UUID variantId, BigDecimal onHandQty,
                                 BigDecimal availableQty, BigDecimal minimumQty, Instant occurredAt) {
    public LowStockAlertEvent(UUID variantId, BigDecimal onHandQty, BigDecimal availableQty, BigDecimal minimumQty) {
        this(UUID.randomUUID(), variantId, onHandQty, availableQty, minimumQty, Instant.now());
    }
}
