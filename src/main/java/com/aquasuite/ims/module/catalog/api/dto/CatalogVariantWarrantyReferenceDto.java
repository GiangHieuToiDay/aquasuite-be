package com.aquasuite.ims.module.catalog.api.dto;

import lombok.Builder;

import java.util.UUID;

/** Public SKU projection used to show a warranty policy's references. */
@Builder
public record CatalogVariantWarrantyReferenceDto(UUID variantId, String sku, String variantName) {
}
