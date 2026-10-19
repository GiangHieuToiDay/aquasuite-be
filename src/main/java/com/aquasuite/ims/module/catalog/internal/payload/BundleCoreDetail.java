package com.aquasuite.ims.module.catalog.internal.payload;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record BundleCoreDetail(
        UUID bundleId,
        String productCode,
        String name,
        String productStatus,
        String publicationStatus,
        Long version,
        int linkedItemsCount,
        BigDecimal originalTotal,
        BigDecimal packagePrice,
        BigDecimal savingAmount,
        BigDecimal discountPercent,
        String priceIssue,
        Instant createdAt,
        Instant updatedAt,
        UUID categoryId,
        UUID brandId,
        String description,
        List<Item> items) {
    public record Item(
            UUID bundleItemId,
            UUID variantId,
            String sku,
            String name,
            int quantity,
            BigDecimal sellingPrice,
            boolean sellable) {}
}
