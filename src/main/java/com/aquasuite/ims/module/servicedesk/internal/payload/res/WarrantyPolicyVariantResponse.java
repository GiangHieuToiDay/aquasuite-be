package com.aquasuite.ims.module.servicedesk.internal.payload.res;

import lombok.Builder;

import java.util.UUID;

@Builder
public record WarrantyPolicyVariantResponse(UUID variantId, String sku, String variantName) {
}
