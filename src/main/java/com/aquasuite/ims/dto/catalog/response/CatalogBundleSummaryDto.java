package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record CatalogBundleSummaryDto(
        UUID bundleId,
        String bundleCode,
        String bundleName,
        BigDecimal packagePrice,
        String status,
        String publicationStatus,
        Instant validFrom,
        Instant validUntil,
        String imageUrl,
        List<CatalogBundleItemDto> items
) {
    @Builder
    public record CatalogBundleItemDto(
            UUID variantId,
            String sku,
            String variantName,
            int quantity,
            BigDecimal sellingPrice,
            Boolean sellable
    ) {}
}
