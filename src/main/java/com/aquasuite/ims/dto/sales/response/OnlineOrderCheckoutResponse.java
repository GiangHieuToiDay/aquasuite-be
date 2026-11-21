package com.aquasuite.ims.dto.sales.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kết quả đơn hàng trực tuyến và thông tin link thanh toán PayOS (UC-18).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineOrderCheckoutResponse {

    private UUID orderId;
    private String orderCode;
    private UUID customerId;
    private String orderChannel;
    private String orderType;
    private String orderStatus;
    private String paymentStatus;
    private String paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private String deliverySchedule;
    private String note;
    private OffsetDateTime createdAt;
    private List<SalesOrderItemResponse> items;
    private PayosPaymentLinkResponse payosPayment;
}

