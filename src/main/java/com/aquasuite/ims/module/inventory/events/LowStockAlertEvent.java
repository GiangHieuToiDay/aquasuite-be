package com.aquasuite.ims.module.inventory.events;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockAlertEvent(
        UUID variantId,
        BigDecimal onHandQty,
        BigDecimal availableQty,
        BigDecimal minimumQty
) {}
