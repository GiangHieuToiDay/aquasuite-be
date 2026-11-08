package com.aquasuite.ims.module.catalog.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record CatalogBundleSummaryDto(
        UUID bundleId,
        String bundleCode,
        String bundleName,
        BigDecimal packagePrice,
        String status,
        List<CatalogBundleItemDto> items
) {
    @Builder
    public record CatalogBundleItemDto(
            UUID variantId,
            String sku,
            String variantName,
            int quantity,
            BigDecimal sellingPrice
    ) {}
}
