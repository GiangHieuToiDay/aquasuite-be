package com.aquasuite.ims.module.inventory.dto;

import com.aquasuite.ims.module.inventory.internal.entity.enums.MovementType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StockAdjustCommand(
        UUID variantId,
        MovementType movementType,
        BigDecimal deltaQty,          // Dương (+) nếu cộng kho, Âm (-) nếu trừ kho
        boolean releaseFromReserved,  // true nếu là Xuất kho từ đơn hàng đã đặt giữ trước đó
        String referenceType,         // 'EXCEL_IMPORT', 'GOODS_RECEIPT', 'STOCK_DISPATCH', 'STOCK_COUNT', v.v.
        String referenceId,           // Lưu jobId hoặc mã/UUID chứng từ
        String note,
        Long actorUserId
) {}
