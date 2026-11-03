package com.aquasuite.ims.module.sales.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO thông tin đơn hàng phục vụ module Finance (UC-23 / UC-70)
 * kiểm tra đơn hàng tồn tại, còn hiệu lực và lấy tổng tiền cần thanh toán.
 */
@Builder
public record SalesOrderPaymentInfoDto(
        UUID orderId,
        String orderCode,
        UUID customerId,
        String orderType,
        String orderChannel,
        String orderStatus,
        String paymentStatus,
        BigDecimal totalAmount,
        boolean validForPayment
) {
}

