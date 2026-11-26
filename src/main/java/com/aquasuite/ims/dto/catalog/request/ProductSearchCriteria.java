package com.aquasuite.ims.dto.catalog.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Builder
public record ProductSearchCriteria(
        @Size(max = 100) String keyword,
        UUID categoryId,
        @Size(max = 100) String categoryCode,
        UUID brandId,
        @Size(max = 100) String brandCode,
        @DecimalMin(value = "0", message = "Giá tối thiểu không được âm") BigDecimal minPrice,
        @DecimalMin(value = "0", message = "Giá tối đa không được âm") BigDecimal maxPrice,
        Boolean inStockOnly,
        @Size(max = 100) String color,
        @Size(max = 100) String size,
        @Size(max = 50) Map<String, String> specifications,
        @Size(max = 64) String sortBy
) {}
