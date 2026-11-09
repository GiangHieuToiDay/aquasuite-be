package com.aquasuite.ims.module.sales.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO thông tin đơn hàng phục vụ module Finance (UC-23 / UC-70)
 * kiểm tra đơn hàng tồn tại, còn hiệu lực và lấy tổng tiền cần thanh toán.
 * @param orderId mã đơn hàng
 * @param orderCode mã đơn hiển thị
 * @param customerId mã khách hàng của đơn
 * @param storeInvoiceId mã hóa đơn cửa hàng; có thể rỗng đối với đơn cũ
 * @param orderType loại đơn hàng
 * @param orderChannel kênh bán hàng
 * @param orderStatus trạng thái xử lý đơn
 * @param paymentStatus trạng thái thanh toán đang lưu tại Sales
 * @param totalAmount tổng tiền phải trả của đơn
 * @param depositAmount số tiền cọc theo thỏa thuận của đơn; có thể null nếu đơn không đặt cọc
 * @param validForPayment đơn có hợp lệ để tiếp nhận thanh toán hay không
 */
@Builder
public record SalesOrderPaymentInfoDto(
        UUID orderId,
        String orderCode,
        UUID customerId,
        UUID storeInvoiceId,
        String orderType,
        String orderChannel,
        String orderStatus,
        String paymentStatus,
        BigDecimal totalAmount,
        BigDecimal depositAmount,
        boolean validForPayment
) {
}

