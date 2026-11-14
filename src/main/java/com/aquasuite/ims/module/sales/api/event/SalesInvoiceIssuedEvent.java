package com.aquasuite.ims.module.sales.api.event;

import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceSummaryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event phát ra khi một hóa đơn chuyển trạng thái từ DRAFT sang ISSUED.
 * Module Fulfillment có thể lắng nghe sự kiện này để sinh phiếu xuất kho cho đúng đợt hàng đã chốt.
 */
public record SalesInvoiceIssuedEvent(
        UUID eventId,
        UUID invoiceId,
        String invoiceCode,
        UUID orderId,
        String orderCode,
        UUID customerId,
        String invoiceType,
        Integer batchNo,
        String batchName,
        BigDecimal totalAmount,
        LocalDate dueDate,
        UUID issuedBy,
        List<SalesInvoiceSummaryDto.SalesInvoiceItemSummaryDto> items,
        Instant occurredAt
) {
    public SalesInvoiceIssuedEvent(
            UUID invoiceId,
            String invoiceCode,
            UUID orderId,
            String orderCode,
            UUID customerId,
            String invoiceType,
            Integer batchNo,
            String batchName,
            BigDecimal totalAmount,
            LocalDate dueDate,
            UUID issuedBy,
            List<SalesInvoiceSummaryDto.SalesInvoiceItemSummaryDto> items
    ) {
        this(
                UUID.randomUUID(),
                invoiceId,
                invoiceCode,
                orderId,
                orderCode,
                customerId,
                invoiceType,
                batchNo,
                batchName,
                totalAmount,
                dueDate,
                issuedBy,
                items,
                Instant.now()
        );
    }
}

