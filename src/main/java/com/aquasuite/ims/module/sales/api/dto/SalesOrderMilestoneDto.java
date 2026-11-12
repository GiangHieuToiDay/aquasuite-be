package com.aquasuite.ims.module.sales.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO chỉ đọc mô tả một mốc thanh toán theo thỏa thuận của đơn hàng.
 *
 * <p>Đây là projection công khai do module Sales cung cấp cho module Finance (UC-23)
 * để tính tiến độ thanh toán theo từng mốc. DTO không chứa số đã trả hay trạng thái
 * thanh toán: phần tiến độ (đã trả/còn thiếu) do Finance tự tính từ các giao dịch
 * {@code RECEIPT}/{@code VERIFIED} của đúng đơn, không nằm trong snapshot mốc.</p>
 *
 * @param milestoneId mã mốc thanh toán
 * @param orderId     mã đơn hàng sở hữu mốc
 * @param stageName   tên đợt/mốc thanh toán theo thỏa thuận
 * @param percent     tỷ lệ phần trăm của mốc trên tổng đơn
 * @param amount      số tiền theo thỏa thuận của mốc (nghĩa vụ cần trả cho mốc)
 * @param sortOrder   thứ tự của mốc trong đơn, dùng để tính tiến độ tuần tự
 */
@Builder
public record SalesOrderMilestoneDto(
        UUID milestoneId,
        UUID orderId,
        String stageName,
        BigDecimal percent,
        BigDecimal amount,
        Integer sortOrder
) {
}
