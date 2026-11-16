package com.aquasuite.ims.module.catalog.internal.payload.res;

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
