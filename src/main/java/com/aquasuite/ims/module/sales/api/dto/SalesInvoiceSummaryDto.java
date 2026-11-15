package com.aquasuite.ims.module.sales.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hợp đồng dữ liệu công khai của một hóa đơn bán hàng (dùng cho Fulfillment đọc hóa đơn ISSUED để tạo phiếu xuất kho).
 */
public record SalesInvoiceSummaryDto(
        UUID invoiceId,
        String invoiceCode,
        UUID orderId,
        String orderCode,
        UUID customerId,
        String invoiceType,
        String status,
        Integer batchNo,
        String batchName,
        OffsetDateTime issueDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        OffsetDateTime issuedAt,
        List<SalesInvoiceItemSummaryDto> items
) {
    public record SalesInvoiceItemSummaryDto(
            UUID invoiceItemId,
            UUID orderItemId,
            UUID variantId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal lineTotal
    ) {
    }
}

