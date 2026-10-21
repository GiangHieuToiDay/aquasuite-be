package com.aquasuite.ims.module.inventory.internal.payload.res;

import com.aquasuite.ims.module.inventory.internal.entity.enums.StockCountStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record StockCountResponse(
        UUID stockCountId,
        String stockCountCode,
        String warehouseCode,
        StockCountStatus status,
        UUID countedBy,
        UUID approvedBy,
        LocalDateTime countDate,
        String note,
        LocalDateTime createdAt,
        List<StockCountItemResponse> items
) {}
