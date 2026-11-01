package com.aquasuite.ims.module.finance.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CreatePaymentTransactionCommand(
        UUID orderId,
        UUID invoiceId,
        UUID customerId,
        BigDecimal amount,
        String paymentMethod,
        String paymentType,
        String category,
        String referenceNo,
        String description,
        UUID createdBy,
        String status
) {}
