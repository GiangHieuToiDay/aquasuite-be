package com.aquasuite.ims.share.exception;

import lombok.Getter;

import java.text.MessageFormat;

@Getter
public class AppException extends RuntimeException {
    private final BaseErrorCode errorCode;
    private final Object[] args;

    public AppException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = null;
    }

    // Hỗ trợ placeholder {0}, {1}... trong message của Enum
    public AppException(BaseErrorCode errorCode, Object... args) {
        super(MessageFormat.format(errorCode.getMessage(), args));
        this.errorCode = errorCode;
        this.args = args;
    }

    // Hỗ trợ ghi đè message hoàn toàn tùy chỉnh khi cần
    public AppException(BaseErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.args = null;
    }
}
