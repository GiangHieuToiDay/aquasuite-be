package com.aquasuite.ims.module.catalog.internal.payload.res;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record SalesCatalogItemResponse(
        UUID variantId,
        String sku,
        String name,
        String brand,
        String category,
        String unit,
        BigDecimal sellingPrice,
        BigDecimal availableStock,
        boolean isCombo,
        String description,
        String imageUrl,
        Map<String, Object> specifications,
        List<String> components
) {}

