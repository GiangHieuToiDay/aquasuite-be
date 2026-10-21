package com.aquasuite.ims.module.catalog.internal.payload.res;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProductFacetResponse(
        List<CategoryFacetItem> categories,
        List<BrandFacetItem> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<String> colors,
        List<String> sizes,
        Map<String, List<String>> technicalSpecifications
) {
    @Builder
    public record CategoryFacetItem(
            UUID categoryId,
            String categoryCode,
            String categoryName,
            long productCount
    ) {}

    @Builder
    public record BrandFacetItem(
            UUID brandId,
            String brandCode,
            String brandName,
            long productCount
    ) {}
}
