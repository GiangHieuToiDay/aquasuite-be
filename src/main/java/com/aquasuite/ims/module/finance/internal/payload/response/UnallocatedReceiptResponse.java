package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A verified order receipt that UC-63 has not allocated to a concrete invoice. */
public record UnallocatedReceiptResponse(
        UUID transactionId,
        String transactionCode,
        UUID orderId,
        BigDecimal amount,
        OffsetDateTime transactionDate) {}
