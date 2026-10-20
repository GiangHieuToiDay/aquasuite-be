package com.aquasuite.ims.module.catalog.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VariantsBulkImportedEvent(UUID eventId, String jobId, List<InitialStockItem> items, Instant occurredAt) {
    public VariantsBulkImportedEvent(String jobId, List<InitialStockItem> items) {
        this(UUID.randomUUID(), jobId, items, Instant.now());
    }
    public record InitialStockItem(UUID variantId, String sku, BigDecimal initialStock, BigDecimal minimumQty) {}
}
