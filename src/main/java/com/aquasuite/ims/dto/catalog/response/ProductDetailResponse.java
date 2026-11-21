package com.aquasuite.ims.dto.catalog.response;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
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
        WarrantyPolicySummaryResponse warrantyPolicy,
        Instant createdAt,
        Instant updatedAt
) {}
