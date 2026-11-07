package com.aquasuite.ims.module.finance.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** Mã lỗi nghiệp vụ cho UC-70 — xác minh/từ chối khoản chuyển khoản đang chờ. */
@Getter
@RequiredArgsConstructor
public enum PaymentVerificationErrorCode implements BaseErrorCode {
    /** Khoản không còn ở trạng thái PENDING (đã VERIFIED/REJECTED) — chống quyết định trùng. */
    NOT_PENDING("VER_001", "Khoản thanh toán đã được xử lý, không thể quyết định lại", HttpStatus.CONFLICT),
    /** Khoản không phải RECEIPT + BANK_TRANSFER đang chờ của đơn. */
    NOT_PENDING_TRANSFER("VER_002", "Chỉ xử lý khoản chuyển khoản đang chờ xác minh của đơn", HttpStatus.CONFLICT),
    /** Số tiền vượt số còn phải trả sau khi khóa đơn; giữ nguyên PENDING. */
    OVER_OBLIGATION("VER_003", "Số tiền vượt số còn phải trả; giữ nguyên chờ xác minh để xử lý ngoài hệ thống", HttpStatus.CONFLICT),
    /** Từ chối nhưng thiếu lý do bắt buộc. */
    REASON_REQUIRED("VER_004", "Lý do từ chối là bắt buộc", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
