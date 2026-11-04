package com.aquasuite.ims.module.finance.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** Mã lỗi nghiệp vụ cho ghi nhận thanh toán và chứng từ của đơn hàng. */
@Getter
@RequiredArgsConstructor
public enum OrderPaymentErrorCode implements BaseErrorCode {
    ORDER_NOT_FOUND("PAY_001", "Không tìm thấy đơn hàng", HttpStatus.NOT_FOUND),
    ORDER_NOT_PAYABLE("PAY_002", "Đơn hàng không thể nhận thanh toán", HttpStatus.CONFLICT),
    AMOUNT_EXCEEDS_BALANCE("PAY_003", "Số tiền vượt số còn phải trả", HttpStatus.CONFLICT),
    DUPLICATE_KEY("PAY_004", "Mã yêu cầu đã được dùng với dữ liệu khác", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND("PAY_005", "Không tìm thấy khoản thanh toán hoặc chứng từ", HttpStatus.NOT_FOUND),
    INVALID_PROOF("PAY_006", "Chứng từ không hợp lệ; chọn PNG, JPEG hoặc PDF tối đa 5 MB", HttpStatus.BAD_REQUEST),
    PROOF_EXISTS("PAY_007", "Khoản thanh toán đã có chứng từ", HttpStatus.CONFLICT),
    PROOF_NOT_ALLOWED("PAY_008", "Chỉ có thể bổ sung chứng từ cho chuyển khoản đang chờ", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
