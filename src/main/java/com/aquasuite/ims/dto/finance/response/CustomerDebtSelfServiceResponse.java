package com.aquasuite.ims.dto.finance.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerDebtSelfServiceResponse(
        BigDecimal outstandingBalance,
        BigDecimal overdueBalance,
        BigDecimal issuedInvoiceReceivable,
        BigDecimal unallocatedAdvanceAmount,
        LocalDate nearestDueDate,
        List<Invoice> invoices,
        List<Order> orders) {

    public record Invoice(
            UUID invoiceId,
            String invoiceCode,
            UUID orderId,
            String orderCode,
            OffsetDateTime issueDate,
            LocalDate dueDate,
            String status,
            BigDecimal totalAmount,
            BigDecimal verifiedPaidAmount,
            BigDecimal outstandingAmount) {}

    public record Order(
            UUID orderId,
            String orderCode,
            String orderStatus,
            BigDecimal totalAmount,
            BigDecimal verifiedPaidAmount,
            BigDecimal outstandingAmount) {}
}
