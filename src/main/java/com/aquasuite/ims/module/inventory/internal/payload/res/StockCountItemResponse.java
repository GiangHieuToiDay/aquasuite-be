package com.aquasuite.ims.module.inventory.internal.payload.res;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StockCountItemResponse(
        UUID stockCountItemId,
        UUID inventoryId,
        UUID variantId,
        BigDecimal systemQty,
        BigDecimal actualQty,
        BigDecimal differenceQty,
        String reason
) {}
