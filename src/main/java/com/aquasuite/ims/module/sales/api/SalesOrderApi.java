package com.aquasuite.ims.module.sales.api;

import com.aquasuite.ims.module.sales.api.dto.RecordOrderPaymentCommand;

/**
 * Public synchronous interface của module Sales (UC-21 / Đơn hàng bán lẻ & Dự án).
 * Cho phép các module khác (như Finance / Quản lý tiền cọc) cập nhật số tiền đã thanh toán và trạng thái đơn hàng.
 */
public interface SalesOrderApi {

    /**
     * Ghi nhận một khoản thanh toán hoặc tiền cọc bổ sung cho đơn hàng.
     * Phương thức này sẽ:
     * 1. Cộng dồn số tiền vào orders.paid_amount.
     * 2. Tự động cập nhật payment_status (PARTIALLY_PAID hoặc PAID).
     * 3. Đồng bộ trạng thái hóa đơn bán lẻ tương ứng.
     *
     * @param command Thông tin lệnh thanh toán bao gồm orderId và số tiền thu
     */
    void recordOrderPayment(RecordOrderPaymentCommand command);
}
