package com.aquasuite.ims.module.finance.internal.payload.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record InvoiceDebtResponse(
        UUID invoiceId,
        String invoiceCode,
        String invoiceSymbol,
        UUID orderId,
        OffsetDateTime issueDate,
        OffsetDateTime signedAt,
        LocalDate dueDate,
        String status,
        BigDecimal totalAmount,
        BigDecimal verifiedPaidAmount,
        BigDecimal outstandingAmount) {}
