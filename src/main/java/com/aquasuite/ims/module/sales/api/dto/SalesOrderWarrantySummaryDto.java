package com.aquasuite.ims.module.sales.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SalesOrderWarrantySummaryDto(
        UUID orderId,
        String orderCode,
        String orderStatus,
        String paymentStatus,
        OffsetDateTime orderDate,
        String deliveryAddress,
        UUID customerId,
        UUID invoiceId,
        String invoiceCode,
        String invoiceType,
        String invoiceStatus,
        OffsetDateTime issueDate,
        BigDecimal invoiceTotalAmount
) {
}

