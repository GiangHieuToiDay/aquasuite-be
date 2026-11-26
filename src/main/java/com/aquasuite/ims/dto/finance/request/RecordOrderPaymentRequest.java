package com.aquasuite.ims.dto.finance.request;

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
        @NotNull(message = "Thiếu mã yêu cầu ghi nhận thanh toán")
        UUID idempotencyKey,
        @NotNull(message = "Vui lòng nhập số tiền")
        @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 13, fraction = 2, message = "Số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal amount,
        @NotNull(message = "Vui lòng chọn phương thức thanh toán")
        Method paymentMethod,
        @Size(max = 255, message = "Mã tham chiếu tối đa 255 ký tự")
        String referenceNo,
        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String description) {
    /** Các phương thức thu tiền được hỗ trợ khi ghi nhận tại quầy. */
    public enum Method {
        /** Tiền mặt nhân viên thực nhận. */
        CASH,
        /** Khoản chuyển khoản khách khai báo, chờ xác minh. */
        BANK_TRANSFER
    }
}
