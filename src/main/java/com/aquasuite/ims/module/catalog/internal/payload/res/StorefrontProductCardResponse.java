package com.aquasuite.ims.module.catalog.internal.payload.res;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record StorefrontProductCardResponse(
        UUID productId,
        String productCode,
        String productName,
        UUID categoryId,
        String categoryName,
        UUID brandId,
        String brandName,
        String thumbnailUrl,
        BigDecimal startingPrice,
        BigDecimal maxPrice,
        String stockStatus,
        String stockBadge,
        BigDecimal totalAvailableStock,
        int totalVariants,
        Map<String, Object> specificationsSummary,
        Instant createdAt
) {}
