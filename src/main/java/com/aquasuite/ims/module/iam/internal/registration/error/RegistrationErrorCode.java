package com.aquasuite.ims.module.iam.internal.registration.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

public enum RegistrationErrorCode implements BaseErrorCode {
    DUPLICATE_ACCOUNT(
            "REG_001",
            "An account with this email address or phone number already exists. Please log in or reset your password.",
            HttpStatus.CONFLICT),
    INVALID_OTP("REG_002", "Invalid OTP code. Please check and try again.", HttpStatus.BAD_REQUEST),
    EXPIRED_OTP(
            "REG_003",
            "The verification code has expired. Please request a new code.",
            HttpStatus.GONE),
    OTP_LIMIT(
            "REG_004",
            "Please wait before requesting another code, or request a new code after expiry.",
            HttpStatus.TOO_MANY_REQUESTS),
    NOT_PENDING(
            "REG_006",
            "Account is not pending activation. Please log in if it is already active.",
            HttpStatus.CONFLICT),
    MAIL_UNAVAILABLE(
            "REG_007",
            "Unable to send verification email. Please request another code.",
            HttpStatus.SERVICE_UNAVAILABLE),
    UNAVAILABLE(
            "REG_008", "Registration is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE),
    LOGIN_REQUIRED(
            "REG_009",
            "Account activated. Please log in with your registered credentials.",
            HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;

    RegistrationErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
