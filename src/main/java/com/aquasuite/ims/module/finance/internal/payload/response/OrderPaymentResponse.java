package com.aquasuite.ims.module.finance.internal.payload.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Thông tin một giao dịch thu tiền trả cho client.
 * @param transactionId mã giao dịch
 * @param transactionCode mã giao dịch hiển thị
 * @param orderId mã đơn được thanh toán
 * @param invoiceId mã hóa đơn được gắn, có thể rỗng với dữ liệu cũ
 * @param amount số tiền của giao dịch
 * @param paymentMethod phương thức tiền mặt hoặc chuyển khoản
 * @param status trạng thái giao dịch
 * @param referenceNo mã tham chiếu nếu có
 * @param description ghi chú nếu có
 * @param rejectReason lý do từ chối (chỉ có khi trạng thái REJECTED, do UC-70 ghi)
 * @param transactionDate thời điểm giao dịch
 * @param createdBy mã nhân viên ghi nhận
 */
public record OrderPaymentResponse(UUID transactionId, String transactionCode, UUID orderId, UUID invoiceId,
        BigDecimal amount, String paymentMethod, String status, String referenceNo,
        String description, String rejectReason, OffsetDateTime transactionDate, UUID createdBy) {}
