package com.aquasuite.ims.module.catalog.internal.payload.res;

import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProductVariantResponse(
        UUID variantId,
        UUID productId,
        String sku,
        String variantName,
        String unit,
        BigDecimal conversionRate,
        String color,
        String size,
        BigDecimal weight,
        Map<String, Object> technicalSpec,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        UUID warrantyPolicyId,
        Boolean serialTracking,
        VariantStatus status,
        List<ProductImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
