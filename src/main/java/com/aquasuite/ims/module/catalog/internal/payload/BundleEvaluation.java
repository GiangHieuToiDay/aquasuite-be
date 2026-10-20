package com.aquasuite.ims.module.catalog.internal.payload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Current price/stock snapshot; no reservation or guarantee for later transactions. */
public record BundleEvaluation(UUID bundleId, Long bundleVersion, Instant evaluatedAt, int quantity,
        BigDecimal packagePrice, BigDecimal totalPackagePrice, BigDecimal originalTotal,
        BigDecimal savingAmount, BigDecimal discountPercent, String priceIssue,
        String availability, boolean purchasable, List<UUID> missingVariantIds, List<Item> items) {
    public record Item(UUID variantId, String sku, String name, int requiredQty, BigDecimal availableQty) {}
}
