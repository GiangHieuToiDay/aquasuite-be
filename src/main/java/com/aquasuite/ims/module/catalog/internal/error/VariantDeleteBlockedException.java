package com.aquasuite.ims.module.catalog.internal.error;

import com.aquasuite.ims.module.catalog.internal.payload.res.VariantDeletionEligibilityResponse;

public class VariantDeleteBlockedException extends RuntimeException {
    private final VariantDeletionEligibilityResponse eligibility;

    public VariantDeleteBlockedException(VariantDeletionEligibilityResponse eligibility) {
        this.eligibility = eligibility;
    }

    public VariantDeletionEligibilityResponse getEligibility() {
        return eligibility;
    }
}
