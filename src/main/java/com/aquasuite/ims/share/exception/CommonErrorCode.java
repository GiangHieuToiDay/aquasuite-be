package com.aquasuite.ims.share.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements BaseErrorCode {
    INTERNAL_SERVER_ERROR("SYS_001", "Lỗi hệ thống nội bộ, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST_PARAM("SYS_002", "Tham số đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("SYS_003", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("SYS_004", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    UNAUTHORIZED("AUTH_001", "Chưa xác thực hoặc phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_002", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    DATA_INTEGRITY_VIOLATION("DB_001", "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
