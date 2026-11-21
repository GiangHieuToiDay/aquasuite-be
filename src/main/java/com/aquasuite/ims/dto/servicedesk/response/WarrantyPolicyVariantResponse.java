package com.aquasuite.ims.dto.servicedesk.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record WarrantyPolicyVariantResponse(UUID variantId, String sku, String variantName) {
}
