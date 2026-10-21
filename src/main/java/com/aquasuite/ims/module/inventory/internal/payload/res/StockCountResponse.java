package com.aquasuite.ims.module.inventory.internal.payload.res;

import com.aquasuite.ims.module.inventory.internal.entity.enums.StockCountStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record StockCountResponse(
        UUID stockCountId,
        String stockCountCode,
        String warehouseCode,
        StockCountStatus status,
        Long countedBy,
        Long approvedBy,
        Instant countDate,
        String note,
        Instant createdAt,
        List<StockCountItemResponse> items
) {}
