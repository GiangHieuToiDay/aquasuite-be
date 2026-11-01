package com.aquasuite.ims.module.sales.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO lệnh ghi nhận thanh toán / tiền cọc bổ sung cho đơn hàng.
 * Dùng làm public contract giữa module Finance và module Sales.
 */
@Builder
public record RecordOrderPaymentCommand(
        UUID orderId,
        BigDecimal amount,
        String paymentMethod,
        String note
) {}
