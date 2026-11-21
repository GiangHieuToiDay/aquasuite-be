package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CatalogWarrantyPolicyDto(
        UUID variantId,
        String sku,
        String variantName,
        String productName,
        Boolean serialTracking,
        UUID warrantyPolicyId,
        String policyCode,
        String policyName,
        Integer warrantyMonths,
        String warrantyTerms,
        String policyStatus,
        UUID brandId,
        UUID categoryId
) {
}

