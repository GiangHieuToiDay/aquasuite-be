package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderPaymentResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderPaymentSummary;
import java.util.List;
import java.util.UUID;

/** Các thao tác ghi nhận và theo dõi thanh toán của một đơn hàng. */
public interface OrderPaymentService {
    /**
     * Ghi nhận khoản thu hoặc trả giao dịch cũ khi gửi lại cùng yêu cầu.
     * @param orderId mã đơn nhận thanh toán
     * @param request thông tin khoản thu và mã chống ghi trùng
     * @return thông tin giao dịch được ghi nhận
     */
    OrderPaymentResponse record(UUID orderId, RecordOrderPaymentRequest request);

    /**
     * Tính số đã xác minh, đang chờ và còn phải trả của đơn.
     * @param orderId mã đơn cần xem
     * @return tổng quan thanh toán
     */
    OrderPaymentSummary summary(UUID orderId);

    /**
     * Lấy các khoản thu đã ghi nhận cho đơn.
     * @param orderId mã đơn cần xem lịch sử
     * @return danh sách giao dịch thu tiền
     */
    List<OrderPaymentResponse> history(UUID orderId);
}
