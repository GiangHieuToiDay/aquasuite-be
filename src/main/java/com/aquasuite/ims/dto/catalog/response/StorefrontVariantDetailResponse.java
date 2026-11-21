package com.aquasuite.ims.dto.catalog.response;

import com.aquasuite.ims.enums.catalog.VariantStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
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
        Instant createdAt,
        Instant updatedAt
) {}
