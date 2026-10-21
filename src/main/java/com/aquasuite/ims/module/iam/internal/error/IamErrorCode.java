package com.aquasuite.ims.module.iam.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IamErrorCode implements BaseErrorCode {

    INVALID_PERMISSION_CODE_FORMAT("IAM_001", "Mã quyền không đúng định dạng UPPER_SNAKE_CASE (1-150 ký tự, bắt đầu bằng chữ cái)", HttpStatus.BAD_REQUEST),
    RESERVED_ROLE_NAMESPACE("IAM_002", "Mã quyền không được sử dụng tiền tố ROLE_", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND("IAM_003", "Không tìm thấy vai trò yêu cầu", HttpStatus.NOT_FOUND),
    PERMISSION_NOT_FOUND("IAM_004", "Không tìm thấy quyền yêu cầu", HttpStatus.NOT_FOUND),
    PERMISSION_CODE_DUPLICATE("IAM_005", "Mã quyền đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    PERMISSION_CURRENTLY_ASSIGNED("IAM_006", "Không thể xóa quyền đang được gán cho vai trò", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
