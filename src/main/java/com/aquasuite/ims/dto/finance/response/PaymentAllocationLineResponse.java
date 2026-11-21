package com.aquasuite.ims.dto.finance.response;

import com.aquasuite.ims.enums.finance.PaymentAllocationType;
import com.aquasuite.ims.enums.finance.PaymentAllocationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentAllocationLineResponse(
        UUID allocationId,
        UUID allocationRequestId,
        UUID transactionId,
        UUID orderId,
        UUID invoiceId,
        BigDecimal allocatedAmount,
        PaymentAllocationType allocationType,
        PaymentAllocationStatus allocationStatus,
        UUID allocatedBy,
        OffsetDateTime allocatedAt,
        String reason,
        UUID reversedBy,
        OffsetDateTime reversedAt,
        String reversalReason) {}
