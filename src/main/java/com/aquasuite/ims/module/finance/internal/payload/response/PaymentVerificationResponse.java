package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Thông tin một khoản chuyển khoản phục vụ kế toán đối chiếu/xem quyết định (UC-70).
 *
 * @param transactionId   mã giao dịch
 * @param transactionCode mã giao dịch hiển thị
 * @param orderId         mã đơn của khoản thu
 * @param customerId      mã khách của đơn
 * @param amount          số tiền khách khai báo đã chuyển
 * @param paymentMethod   phương thức (luôn BANK_TRANSFER trong UC-70)
 * @param status          trạng thái: PENDING, VERIFIED hoặc REJECTED
 * @param referenceNo     mã tham chiếu chuyển khoản (nếu có)
 * @param description     ghi chú của nhân viên khi ghi nhận (nếu có)
 * @param rejectReason    lý do từ chối (chỉ có khi REJECTED)
 * @param transactionDate thời điểm ghi nhận khoản thu
 * @param createdBy       người ghi nhận khoản thu (UC-23)
 * @param approvedBy      người ra quyết định xác minh/từ chối (UC-70)
 * @param approvedAt      thời điểm ra quyết định
 */
public record PaymentVerificationResponse(
        UUID transactionId,
        String transactionCode,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String paymentMethod,
        String status,
        String referenceNo,
        String description,
        String rejectReason,
        OffsetDateTime transactionDate,
        UUID createdBy,
        UUID approvedBy,
        OffsetDateTime approvedAt
) {}
