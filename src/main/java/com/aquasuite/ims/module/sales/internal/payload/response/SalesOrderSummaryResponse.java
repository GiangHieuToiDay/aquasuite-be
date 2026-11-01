package com.aquasuite.ims.module.sales.internal.payload.response;

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
public class SalesOrderSummaryResponse {

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
    private BigDecimal remainingAmount;
    private String deliveryAddress;
    private int itemCount;
    private OffsetDateTime createdAt;
}
