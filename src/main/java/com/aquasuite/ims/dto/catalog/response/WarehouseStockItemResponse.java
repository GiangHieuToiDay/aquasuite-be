package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record WarehouseStockItemResponse(
        String warehouseCode,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        BigDecimal minimumQty,
        boolean isLowStock
) {}
