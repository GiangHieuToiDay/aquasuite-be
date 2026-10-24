package com.aquasuite.ims.module.catalog.internal.payload.req;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProductSearchCriteria(
        String keyword,
        UUID categoryId,
        String categoryCode,
        UUID brandId,
        String brandCode,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStockOnly,
        String color,
        String size,
        Map<String, String> specifications,
        String sortBy
) {}
