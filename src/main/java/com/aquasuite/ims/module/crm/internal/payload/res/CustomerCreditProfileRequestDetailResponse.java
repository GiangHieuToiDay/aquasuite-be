package com.aquasuite.ims.module.crm.internal.payload.res;

import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shop Owner-only response. Never return this payload to Sales Staff. */
@Builder
public record CustomerCreditProfileRequestDetailResponse(
        UUID requestId,
        UUID customerId,
        UUID requesterId,
        BigDecimal requestedCreditLimit,
        Integer requestedCreditTermDays,
        String reason,
        String identityDocumentNumber,
        String residentialAddress,
        String worksiteAddress,
        CustomerCreditProfileRequestStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        Instant createdAt,
        Instant updatedAt,
        long qualifiedOrderCount) {
}
