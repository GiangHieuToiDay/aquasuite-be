package com.aquasuite.ims.dto.finance.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentAllocationBatchResponse(
        UUID allocationRequestId,
        UUID receiptId,
        UUID orderId,
        BigDecimal allocatedAmount,
        BigDecimal remainingReceiptAmount,
        List<PaymentAllocationLineResponse> allocations) {}
