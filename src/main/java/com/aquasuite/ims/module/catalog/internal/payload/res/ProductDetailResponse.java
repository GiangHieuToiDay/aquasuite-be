package com.aquasuite.ims.module.catalog.internal.payload.res;

import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProductDetailResponse(
        UUID productId,
        String productCode,
        String productName,
        CategoryBriefResponse category,
        BrandBriefResponse brand,
        String description,
        Map<String, Object> specifications,
        ProductType productType,
        ProductStatus status,
        List<ProductImageResponse> images,
        List<ProductVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt
) {}
