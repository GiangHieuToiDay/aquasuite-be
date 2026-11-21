package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record WarrantyPolicySummaryResponse(
        UUID warrantyPolicyId,
        String policyCode,
        String policyName,
        Integer warrantyMonths,
        String warrantyTerms,
        String policyStatus
) {
}
