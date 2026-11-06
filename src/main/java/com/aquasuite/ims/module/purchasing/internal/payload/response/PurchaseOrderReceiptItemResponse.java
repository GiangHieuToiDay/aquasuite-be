package com.aquasuite.ims.module.purchasing.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderReceiptItemResponse {
    private UUID goodsReceiptId;
    private String receiptCode;
    private String status;
    private LocalDate receiptDate;
    private String supplierInvoiceNo;
    private BigDecimal totalAmount;
    private OffsetDateTime confirmedAt;
}