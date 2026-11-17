package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read-only queue row for a verified receipt that UC-63 can still allocate. */
public record PaymentAllocationReceiptResponse(
        UUID receiptId,
        String receiptCode,
        UUID orderId,
        UUID customerId,
        BigDecimal receiptAmount,
        BigDecimal allocatedAmount,
        BigDecimal remainingAmount,
        String paymentMethod,
        OffsetDateTime receivedAt) {}
