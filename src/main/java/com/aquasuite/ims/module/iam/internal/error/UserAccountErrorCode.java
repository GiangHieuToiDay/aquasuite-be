package com.aquasuite.ims.module.iam.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserAccountErrorCode implements BaseErrorCode {
    USER_NOT_FOUND("IAM_ACC_001", "Không tìm thấy tài khoản người dùng.", HttpStatus.NOT_FOUND),
    DUPLICATE_ACCOUNT("MSG25", "Địa chỉ email hoặc số điện thoại này đã được đăng ký cho một tài khoản khác.", HttpStatus.CONFLICT),
    ROLE_NOT_FOUND("IAM_ACC_003", "Một hoặc nhiều vai trò được chọn hiện không khả dụng.", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION("IAM_ACC_004", "Việc chuyển đổi trạng thái tài khoản này không được phép.", HttpStatus.BAD_REQUEST),
    SELF_STATUS_CHANGE_FORBIDDEN("IAM_ACC_005", "Bạn không thể hủy kích hoạt hoặc khóa tài khoản của chính mình.", HttpStatus.BAD_REQUEST),
    LAST_STORE_OWNER_PROTECTED("IAM_ACC_006", "Chủ cửa hàng đang hoạt động cuối cùng không thể bị hủy kích hoạt, khóa hoặc tước bỏ vai trò này.", HttpStatus.CONFLICT),
    INVALID_TEMPORARY_PASSWORD("IAM_ACC_007", "Mật khẩu tạm thời không đáp ứng các yêu cầu bảo mật hoặc thông tin xác nhận không khớp.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
