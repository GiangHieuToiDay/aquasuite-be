package com.aquasuite.ims.module.inventory.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StockSummaryDto(
        UUID inventoryId,
        UUID variantId,
        String warehouseCode,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        BigDecimal minimumQty,
        boolean isLowStock
) {}
