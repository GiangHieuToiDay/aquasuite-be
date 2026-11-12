package com.aquasuite.ims.module.inventory.internal.payload.res;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin một phiếu nhập kho trả về cho client (UC-32).
 *
 * <p>Theo Hướng B, phiếu được trả về luôn ở trạng thái CONFIRMED (đã cộng tồn và bất biến).
 * Trường {@code items} chỉ có mặt ở API chi tiết; API danh sách để null nhằm nhẹ payload.</p>
 */
@Builder
public record GoodsReceiptResponse(
        UUID goodsReceiptId,
        String receiptCode,
        UUID purchaseOrderId,
        String status,
        LocalDate receiptDate,
        String supplierInvoiceNo,
        String invoiceImageUrl,
        BigDecimal totalAmount,
        String note,
        UUID createdBy,
        UUID confirmedBy,
        OffsetDateTime confirmedAt,
        OffsetDateTime createdAt,
        List<GoodsReceiptItemResponse> items
) {}
