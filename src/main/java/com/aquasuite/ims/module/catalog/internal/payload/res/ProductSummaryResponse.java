package com.aquasuite.ims.module.catalog.internal.payload.res;

import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        String origin,
        ProductType productType,
        ProductStatus status,
        String primaryImageUrl,
        BigDecimal minSellingPrice,
        BigDecimal maxSellingPrice,
        int totalVariants,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
