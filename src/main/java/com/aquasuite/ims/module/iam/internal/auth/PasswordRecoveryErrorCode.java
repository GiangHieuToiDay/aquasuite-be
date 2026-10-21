package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PasswordRecoveryErrorCode implements BaseErrorCode {
    INCORRECT_CURRENT_PASSWORD("IAM_PWD_001", "Current password is incorrect.", HttpStatus.BAD_REQUEST),
    WEAK_OR_MISMATCHED_PASSWORD("IAM_PWD_002", "New password does not meet security requirements or does not match confirmation.", HttpStatus.BAD_REQUEST),
    SAME_AS_CURRENT_PASSWORD("IAM_PWD_003", "New password cannot be the same as current password.", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("IAM_PWD_004", "No account found with the provided email or phone number.", HttpStatus.NOT_FOUND),
    INVALID_OR_EXPIRED_OTP("IAM_PWD_005", "The recovery OTP code is invalid or has expired (10 minutes limit).", HttpStatus.BAD_REQUEST),
    EMAIL_RATE_LIMIT_EXCEEDED("IAM_PWD_006", "Maximum password reset requests reached for today. Please try again tomorrow.", HttpStatus.TOO_MANY_REQUESTS),
    SMS_CHANNEL_NOT_SUPPORTED_YET("IAM_PWD_007", "SMS OTP recovery is currently unavailable. Please use Email recovery.", HttpStatus.NOT_IMPLEMENTED);
    private final String code; private final String message; private final HttpStatus status;
}
