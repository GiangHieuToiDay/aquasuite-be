package com.aquasuite.ims.dto.iam.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreditApprovalGrantResponse(
        String approvalToken,
        Instant expiresAt,
        UUID approvedBy) {}
