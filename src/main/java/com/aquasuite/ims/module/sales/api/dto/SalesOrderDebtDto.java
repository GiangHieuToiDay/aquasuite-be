package com.aquasuite.ims.module.sales.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only order data consumed by the Finance receivable module. */
public record SalesOrderDebtDto(
        UUID orderId,
        UUID customerId,
        String orderCode,
        String orderChannel,
        String orderStatus,
        String paymentStatus,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        LocalDate dueDate) {}
