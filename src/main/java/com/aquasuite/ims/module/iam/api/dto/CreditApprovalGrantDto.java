package com.aquasuite.ims.module.iam.api.dto;

import java.util.UUID;

/** Entity-free proof that a Shop Owner approved one immutable credit-profile request. */
public record CreditApprovalGrantDto(UUID approverId, UUID requesterId, UUID customerId) {}
