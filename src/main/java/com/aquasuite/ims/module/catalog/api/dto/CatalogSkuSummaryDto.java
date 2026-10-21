package com.aquasuite.ims.module.catalog.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CatalogSkuSummaryDto(
        UUID variantId,
        UUID productId,
        String sku,
        String variantName,
        String unit,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        Boolean serialTracking,
        String status
) {}
