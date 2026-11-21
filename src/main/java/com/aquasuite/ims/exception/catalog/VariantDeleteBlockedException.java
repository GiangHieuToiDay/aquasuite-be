package com.aquasuite.ims.exception.catalog;

import com.aquasuite.ims.dto.catalog.response.VariantDeletionEligibilityResponse;

public class VariantDeleteBlockedException extends RuntimeException {
    private final VariantDeletionEligibilityResponse eligibility;

    public VariantDeleteBlockedException(VariantDeletionEligibilityResponse eligibility) {
        this.eligibility = eligibility;
    }

    public VariantDeletionEligibilityResponse getEligibility() {
        return eligibility;
    }
}
