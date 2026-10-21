package com.aquasuite.ims.module.catalog.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event bắn sang module inventory khi hoàn tất import hàng loạt SKU mới.
 */
public record VariantsBulkImportedEvent(String jobId, List<InitialStockItem> items) {
    public record InitialStockItem(
            UUID variantId, String sku, BigDecimal initialStock, BigDecimal minimumQty) {}
}
