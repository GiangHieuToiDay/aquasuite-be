package com.aquasuite.ims.module.inventory.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StockSummaryDto(
        UUID variantId,
        String warehouseCode,
        String binLocation,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        BigDecimal minimumQty
) {}
