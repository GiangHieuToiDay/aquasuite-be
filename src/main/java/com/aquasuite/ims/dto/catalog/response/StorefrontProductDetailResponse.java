package com.aquasuite.ims.dto.catalog.response;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record StorefrontProductDetailResponse(
        UUID productId,
        String productCode,
        String productName,
        CategoryBriefResponse category,
        BrandBriefResponse brand,
        String description,
        Map<String, Object> specifications,
        ProductType productType,
        ProductStatus status,
        BigDecimal startingPrice,
        String stockStatus,
        String stockBadge,
        List<ProductImageResponse> images,
        List<String> cadBlueprints,
        String installationManualPdf,
        String warrantyTerms,
        List<StorefrontVariantDetailResponse> variants,
        Instant createdAt,
        Instant updatedAt
) {}
