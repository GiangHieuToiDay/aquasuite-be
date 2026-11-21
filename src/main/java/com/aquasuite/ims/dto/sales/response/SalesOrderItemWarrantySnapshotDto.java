package com.aquasuite.ims.dto.sales.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SalesOrderItemWarrantySnapshotDto(
        UUID orderItemId,
        UUID orderId,
        String orderCode,
        String orderStatus,
        String paymentStatus,
        OffsetDateTime orderDate,
        UUID customerId,
        String deliveryAddress,
        UUID variantId,
        BigDecimal quantity,
        String sku,
        String productName,
        String variantName
) {
}

