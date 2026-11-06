package com.aquasuite.ims.module.inventory.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record GoodsReceiptSummaryDto(
        UUID goodsReceiptId,
        String receiptCode,
        UUID purchaseOrderId,
        String status,
        LocalDate receiptDate,
        String supplierInvoiceNo,
        BigDecimal totalAmount,
        String note,
        UUID createdBy,
        UUID confirmedBy,
        OffsetDateTime confirmedAt
) {}