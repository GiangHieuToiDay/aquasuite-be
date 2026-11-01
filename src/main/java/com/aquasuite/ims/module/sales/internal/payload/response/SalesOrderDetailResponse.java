package com.aquasuite.ims.module.sales.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderDetailResponse {

    private UUID orderId;
    private String orderCode;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID salesStaffId;
    private String salesStaffName;
    private String orderType;
    private String orderChannel;
    private String orderStatus;
    private String paymentStatus;
    private OffsetDateTime orderDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String deliveryAddress;
    private String note;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Builder.Default
    private List<SalesOrderItemResponse> items = new ArrayList<>();

    private SalesInvoiceSummaryResponse invoice;

    @Builder.Default
    private List<com.aquasuite.ims.module.finance.api.dto.PaymentTransactionResponseDto> paymentTransactions = new ArrayList<>();
}
