package com.aquasuite.ims.module.finance.internal.payload.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only order balance displayed in the customer debt detail projection. */
@Builder
public record OrderDebtResponse(
        UUID orderId,
        String orderCode,
        String orderChannel,
        String orderStatus,
        String paymentStatus,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal verifiedPaidAmount,
        BigDecimal outstandingAmount,
        BigDecimal codHeldAmount,
        BigDecimal customerCollectibleAmount) {}
