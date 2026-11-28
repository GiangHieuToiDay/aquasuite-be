package com.aquasuite.ims.dto.inventory.response;

import com.aquasuite.ims.enums.inventory.MovementType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng biến động thẻ kho đã được làm giàu thông tin chứng từ (PO, GRN, Đơn bán) và diễn giải tiếng Việt sạch.
 */
@Builder
public record ReadableStockMovementResponse(
        UUID movementId,
        Instant createdAt,
        MovementType movementType,
        String movementTypeLabel,
        BigDecimal quantity,
        BigDecimal balanceAfter,
        String referenceType,
        String documentCode,
        UUID goodsReceiptId,
        UUID purchaseOrderId,
        String poCode,
        String poStatus,
        String supplierName,
        UUID deliveryId,
        UUID orderId,
        String orderCode,
        UUID actorId,
        String actorName,
        String displayNote
) {}
