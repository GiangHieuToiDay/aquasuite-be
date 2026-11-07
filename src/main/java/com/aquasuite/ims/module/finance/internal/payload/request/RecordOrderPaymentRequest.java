package com.aquasuite.ims.module.finance.internal.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thông tin nhân viên gửi khi ghi nhận một khoản thanh toán tại quầy.
 * @param idempotencyKey mã duy nhất cho lần ghi; dùng lại khi gửi lại cùng yêu cầu
 * @param amount số tiền ghi nhận cho đơn, lớn hơn 0 và tối đa hai chữ số thập phân
 * @param paymentMethod tiền mặt hoặc chuyển khoản
 * @param referenceNo mã tham chiếu chuyển khoản nếu có
 * @param description ghi chú thanh toán nếu có
 */
public record RecordOrderPaymentRequest(
        @NotNull UUID idempotencyKey,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull Method paymentMethod,
        @Size(max = 255) String referenceNo,
        @Size(max = 1000) String description) {
    /** Các phương thức thu tiền được hỗ trợ khi ghi nhận tại quầy. */
    public enum Method {
        /** Tiền mặt nhân viên thực nhận. */
        CASH,
        /** Khoản chuyển khoản khách khai báo, chờ xác minh. */
        BANK_TRANSFER
    }
}
