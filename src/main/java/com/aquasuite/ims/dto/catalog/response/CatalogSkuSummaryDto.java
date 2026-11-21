package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CatalogSkuSummaryDto(
        UUID variantId,
        UUID productId,
        String sku,
        String variantName,
        String productName,
        String unit,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        Boolean serialTracking,
        String status,
        String productStatus,
        String imageUrl
) {}
