package com.aquasuite.ims.module.iam.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    INVALID_CREDENTIALS("AUTH_003", "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH_004", "Refresh token không hợp lệ", HttpStatus.UNAUTHORIZED),
    EXPIRED_REFRESH_TOKEN(
            "AUTH_005", "Refresh token hoặc phiên đã hết hạn", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(
            "AUTH_006",
            "Refresh token đã được sử dụng; phiên liên quan đã bị thu hồi",
            HttpStatus.UNAUTHORIZED),
    SESSION_REVOKED("AUTH_007", "Phiên đã bị thu hồi", HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_ACTIVE("AUTH_008", "Tài khoản không ở trạng thái hoạt động", HttpStatus.FORBIDDEN),
    AUTH_SERVICE_UNAVAILABLE(
            "AUTH_009", "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
