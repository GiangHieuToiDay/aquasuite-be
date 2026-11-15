package com.aquasuite.ims.module.sales.api.dto;

import lombok.Builder;

import java.util.UUID;

/** Read-only order-line snapshot for consumers that own a scalar orderItemId. */
@Builder
public record SalesOrderItemSummaryDto(UUID orderItemId, UUID orderId, String orderCode,
                                       UUID variantId, String sku, String productName, String variantName) {
}
