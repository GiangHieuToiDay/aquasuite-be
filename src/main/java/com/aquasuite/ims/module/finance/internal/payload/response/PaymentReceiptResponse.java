package com.aquasuite.ims.module.finance.internal.payload.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Safe payment ledger projection: proof URLs and other sensitive payloads are excluded. */
@Builder
public record PaymentReceiptResponse(
        UUID transactionId,
        String transactionCode,
        UUID orderId,
        UUID invoiceId,
        BigDecimal amount,
        String paymentMethod,
        OffsetDateTime transactionDate,
        String status) {}
