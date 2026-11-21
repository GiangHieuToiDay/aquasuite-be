package com.aquasuite.ims.dto.inventory.response;

import com.aquasuite.ims.enums.inventory.StockCountStatus;
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
        UUID countedBy,
        UUID approvedBy,
        Instant countDate,
        String note,
        Instant createdAt,
        List<StockCountItemResponse> items
) {}

