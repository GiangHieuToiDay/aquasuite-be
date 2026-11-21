package com.aquasuite.ims.dto.catalog.response;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record ProductSummaryResponse(
        UUID productId,
        String productCode,
        String productName,
        UUID categoryId,
        String categoryName,
        UUID brandId,
        String brandName,
        ProductType productType,
        ProductStatus status,
        String primaryImageUrl,
        BigDecimal minSellingPrice,
        BigDecimal maxSellingPrice,
        int totalVariants,
        Instant createdAt,
        Instant updatedAt
) {}
