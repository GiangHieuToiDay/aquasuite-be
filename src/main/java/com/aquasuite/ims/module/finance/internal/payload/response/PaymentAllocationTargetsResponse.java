package com.aquasuite.ims.module.finance.internal.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentAllocationTargetsResponse(
        UUID receiptId,
        String receiptCode,
        UUID orderId,
        UUID customerId,
        BigDecimal receiptAmount,
        BigDecimal allocatedAmount,
        BigDecimal remainingAmount,
        List<PaymentAllocationTargetResponse> invoices) {}
