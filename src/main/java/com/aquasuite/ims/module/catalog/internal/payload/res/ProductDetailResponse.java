package com.aquasuite.ims.module.catalog.internal.payload.res;

import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductType;
import lombok.Builder;

import java.time.LocalDateTime;
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
