package com.aquasuite.ims.module.sales.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only invoice data consumed by the Finance receivable module. */
public record SalesInvoiceDebtDto(
        UUID invoiceId,
        UUID orderId,
        UUID customerId,
        String invoiceCode,
        String invoiceSymbol,
        OffsetDateTime issueDate,
        OffsetDateTime signedAt,
        Integer creditTermDays,
        LocalDate dueDate,
        String status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        UUID originalInvoiceId) {}
