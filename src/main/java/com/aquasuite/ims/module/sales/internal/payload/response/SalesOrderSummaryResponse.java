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
    private UUID quotationId;
    private String quotationCode;
    private String projectName;
    private BigDecimal depositPercent;
    private BigDecimal depositAmount;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private com.aquasuite.ims.module.finance.api.dto.OrderReceivableView payment;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String depositStatus;
    private Integer creditTermDays;
    private BigDecimal remainingDebtAmount;
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
    private String deliveryAddress;
    private int itemCount;
    private OffsetDateTime createdAt;
}
