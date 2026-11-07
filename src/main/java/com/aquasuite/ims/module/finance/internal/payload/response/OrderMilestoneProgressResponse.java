package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Tổng quan tiến độ thanh toán theo các mốc của một đơn hàng (UC-23).
 *
 * <p>Gồm số liệu toàn đơn (giống {@link OrderPaymentSummary}: đã xác minh, đang chờ,
 * còn phải trả) và danh sách tiến độ từng mốc. Toàn bộ số liệu được tính trong cùng
 * một ảnh chụp dữ liệu nhất quán và không tạo thêm giao dịch thu tiền. Danh sách mốc
 * rỗng khi đơn không có mốc nào; khi đó caller vẫn dùng số liệu toàn đơn.</p>
 *
 * @param orderId      mã đơn được tính tiến độ
 * @param totalAmount  tổng tiền đơn
 * @param verifiedPaid tổng khoản thu đã xác minh của đơn
 * @param pendingAmount tổng khoản thu đang chờ xác minh, chưa tính vào mốc
 * @param remaining    số tiền còn phải trả của toàn đơn = max(totalAmount - verifiedPaid, 0)
 * @param hasMilestones đơn có mốc thanh toán hay không
 * @param milestones   tiến độ từng mốc theo thứ tự thỏa thuận
 */
public record OrderMilestoneProgressResponse(
        UUID orderId,
        BigDecimal totalAmount,
        BigDecimal verifiedPaid,
        BigDecimal pendingAmount,
        BigDecimal remaining,
        boolean hasMilestones,
        List<OrderMilestoneProgress> milestones
) {}
