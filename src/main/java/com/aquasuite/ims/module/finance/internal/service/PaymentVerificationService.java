package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.response.PaymentVerificationPageResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentVerificationResponse;

import java.util.UUID;

/**
 * UC-70 — Kế toán xem, xác minh hoặc từ chối các khoản chuyển khoản đang chờ.
 * Chỉ khoản được xác minh thành công mới được tính vào tiền đã thu của đơn.
 */
public interface PaymentVerificationService {

    /**
     * Liệt kê các khoản chuyển khoản theo trạng thái, có thể lọc theo đơn.
     * @param orderId mã đơn để lọc; null nghĩa là tất cả đơn
     * @param status trạng thái cần lọc (PENDING, VERIFIED, REJECTED); null/blank mặc định PENDING
     * @param page chỉ số trang (từ 0)
     * @param size kích thước trang
     * @return trang các khoản phù hợp
     */
    PaymentVerificationPageResponse list(UUID orderId, String status, int page, int size);

    /**
     * Xem chi tiết một khoản chuyển khoản để đối chiếu.
     * @param paymentId mã giao dịch
     * @return thông tin khoản chuyển khoản
     */
    PaymentVerificationResponse detail(UUID paymentId);

    /**
     * Xác minh một khoản đang chờ: khóa đơn, tính lại nghĩa vụ và chuyển sang VERIFIED
     * nếu không vượt số còn phải trả; cập nhật trạng thái thanh toán của đơn trong cùng transaction.
     * @param paymentId mã giao dịch cần xác minh
     * @return khoản sau khi xác minh
     */
    PaymentVerificationResponse verify(UUID paymentId);

    /**
     * Từ chối một khoản đang chờ với lý do bắt buộc; không tính là đã thu.
     * @param paymentId mã giao dịch cần từ chối
     * @param reason lý do từ chối (bắt buộc)
     * @return khoản sau khi từ chối
     */
    PaymentVerificationResponse reject(UUID paymentId, String reason);
}
