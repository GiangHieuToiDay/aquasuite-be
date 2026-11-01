package com.aquasuite.ims.module.sales.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceSummaryResponse {

    private UUID invoiceId;
    private String invoiceCode;
    private UUID orderId;
    private UUID customerId;
    private String invoiceType;
    private OffsetDateTime issueDate;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String status;
    private UUID createdBy;
    private OffsetDateTime createdAt;
}
