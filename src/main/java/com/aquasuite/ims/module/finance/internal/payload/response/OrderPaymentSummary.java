package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tổng quan thanh toán được tính từ đơn và các khoản thu của đơn.
 * @param orderId mã đơn được tính
 * @param totalAmount tổng tiền đơn
 * @param verifiedPaid tổng khoản thu đã xác minh
 * @param pendingAmount tổng khoản thu đang chờ xác minh, chưa trừ vào số còn phải trả
 * @param remaining số tiền còn phải trả sau khi trừ khoản đã xác minh
 * @param paymentStatus trạng thái thanh toán tính được
 */
public record OrderPaymentSummary(UUID orderId, BigDecimal totalAmount, BigDecimal verifiedPaid,
        BigDecimal pendingAmount, BigDecimal remaining, String paymentStatus) {}
