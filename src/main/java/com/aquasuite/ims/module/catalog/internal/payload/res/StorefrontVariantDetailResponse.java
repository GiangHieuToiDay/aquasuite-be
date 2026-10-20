package com.aquasuite.ims.module.catalog.internal.payload.res;

import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record StorefrontVariantDetailResponse(
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
        BigDecimal sellingPrice,
        UUID warrantyPolicyId,
        Boolean serialTracking,
        VariantStatus status,
        List<ProductImageResponse> images,
        String stockStatus,
        String stockBadge,
        BigDecimal availableQuantity,
        boolean canInstantBuy,
        boolean allowNotifyOrQuote,
        boolean isSalesStaff,
        List<WarehouseStockItemResponse> warehouseBreakdown,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
