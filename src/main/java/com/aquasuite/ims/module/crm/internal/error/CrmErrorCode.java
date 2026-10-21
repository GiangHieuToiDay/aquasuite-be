package com.aquasuite.ims.module.crm.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CrmErrorCode implements BaseErrorCode {
    INVALID_AVATAR("MSG17", "Avatar must be a JPEG, PNG, or WEBP image under 5MB.", HttpStatus.BAD_REQUEST),
    DUPLICATE_PHONE("MSG25", "Địa chỉ email hoặc số điện thoại này đã được đăng ký cho một tài khoản khác.", HttpStatus.CONFLICT),
    ADDRESS_NOT_FOUND("CRM_001", "Không tìm thấy địa chỉ giao hàng yêu cầu.", HttpStatus.NOT_FOUND),
    FORBIDDEN_ADDRESS_ACCESS("CRM_002", "Bạn không có quyền thao tác trên địa chỉ giao hàng này.", HttpStatus.FORBIDDEN),
    CUSTOMER_NOT_FOUND("CRM_003", "Không tìm thấy hồ sơ khách hàng tương ứng với tài khoản này.", HttpStatus.NOT_FOUND),
    AVATAR_UPLOAD_FAILED("CRM_005", "Tải lên ảnh đại diện thất bại. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
