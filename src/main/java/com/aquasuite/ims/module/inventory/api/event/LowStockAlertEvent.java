package com.aquasuite.ims.module.inventory.api.event;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockAlertEvent(
        UUID variantId,
        BigDecimal onHandQty,
        BigDecimal availableQty,
        BigDecimal minimumQty
) {}
