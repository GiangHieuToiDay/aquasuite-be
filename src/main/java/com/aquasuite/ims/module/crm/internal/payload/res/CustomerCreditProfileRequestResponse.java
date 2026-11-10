package com.aquasuite.ims.module.crm.internal.payload.res;

import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record CustomerCreditProfileRequestResponse(
        UUID requestId,
        UUID customerId,
        UUID requesterId,
        BigDecimal requestedCreditLimit,
        Integer requestedCreditTermDays,
        CustomerCreditProfileRequestStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        Instant createdAt,
        boolean appliedImmediately,
        long qualifiedOrderCount,
        boolean identityEvidenceProvided) {
}
