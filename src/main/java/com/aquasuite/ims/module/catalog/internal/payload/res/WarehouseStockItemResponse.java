package com.aquasuite.ims.module.catalog.internal.payload.res;

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
