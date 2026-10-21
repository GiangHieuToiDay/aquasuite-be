package com.aquasuite.ims.module.catalog.internal.variant;

import com.aquasuite.ims.module.catalog.internal.variant.dto.VariantDeletionEligibilityResponse;

public class VariantDeleteBlockedException extends RuntimeException {
    private final VariantDeletionEligibilityResponse eligibility;

    public VariantDeleteBlockedException(VariantDeletionEligibilityResponse eligibility) {
        this.eligibility = eligibility;
    }

    public VariantDeletionEligibilityResponse getEligibility() {
        return eligibility;
    }
}
