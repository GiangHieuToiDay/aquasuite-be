package com.aquasuite.ims.module.finance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ảnh chụp tình hình thu tiền của một đơn, phục vụ các module khác (ví dụ UC-29 giữ hàng theo cọc).
 *
 * <p>Chỉ khoản thu đã {@code VERIFIED} được tính vào {@code verifiedPaid}; khoản {@code PENDING}
 * (chuyển khoản chờ xác minh UC-70) được thể hiện riêng và KHÔNG tính là đã thu. Do đó
 * {@code depositSatisfied} chỉ bật khi tiền thực nhận đã đủ mức cọc — khoản chờ chưa đủ điều kiện giữ hàng.</p>
 *
 * @param orderId         mã đơn
 * @param totalAmount     tổng tiền đơn
 * @param verifiedPaid    tổng tiền đã xác minh (đã thực thu)
 * @param pendingAmount   tổng tiền đang chờ xác minh (chưa tính đã thu)
 * @param remaining       số còn phải trả = max(totalAmount - verifiedPaid, 0)
 * @param paymentStatus   trạng thái thanh toán của đơn tại Sales (UNPAID/PARTIALLY_PAID/PAID)
 * @param depositRequired mức cọc cần đạt: lấy theo deposit_amount của đơn, nếu không có thì theo mốc đầu tiên; 0 nếu không xác định
 * @param depositSatisfied đã thu đủ cọc hay chưa (verifiedPaid ≥ depositRequired khi depositRequired > 0)
 */
public record OrderReceivableView(
        UUID orderId,
        BigDecimal totalAmount,
        BigDecimal verifiedPaid,
        BigDecimal pendingAmount,
        BigDecimal remaining,
        String paymentStatus,
        BigDecimal depositRequired,
        boolean depositSatisfied
) {}
