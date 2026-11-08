package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tiến độ thanh toán của một mốc theo thỏa thuận của đơn hàng (UC-23).
 *
 * <p>Số đã trả được tính từ các khoản thu {@code RECEIPT}/{@code VERIFIED} của đúng đơn,
 * phân bổ lần lượt vào các mốc theo thứ tự; phần dư của mốc trước chảy sang mốc sau.
 * Khoản {@code PENDING} không được tính vào đây. Đây là tiến độ theo thỏa thuận của đơn,
 * không phải phân bổ vào hóa đơn điện tử (UC-63).</p>
 *
 * @param milestoneId     mã mốc thanh toán
 * @param stageName       tên đợt/mốc thanh toán
 * @param sortOrder       thứ tự của mốc trong đơn
 * @param agreedAmount    số tiền theo thỏa thuận của mốc (nghĩa vụ)
 * @param paidAmount      số tiền đã xác minh được phân bổ vào mốc này
 * @param remainingAmount số tiền còn thiếu của mốc = agreedAmount - paidAmount
 */
public record OrderMilestoneProgress(
        UUID milestoneId,
        String stageName,
        Integer sortOrder,
        BigDecimal agreedAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount
) {}
