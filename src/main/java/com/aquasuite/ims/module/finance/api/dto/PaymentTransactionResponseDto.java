package com.aquasuite.ims.module.finance.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentTransactionResponseDto(
        UUID transactionId,
        String transactionCode,
        String transactionType,
        String paymentType,
        String category,
        UUID customerId,
        UUID orderId,
        UUID invoiceId,
        BigDecimal amount,
        String paymentMethod,
        String referenceNo,
        String status,
        String description,
        OffsetDateTime transactionDate,
        UUID createdBy
) {}
