package com.aquasuite.ims.exception.crm;

import com.aquasuite.ims.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CrmErrorCode implements BaseErrorCode {
    INVALID_AVATAR("MSG17", "Avatar must be a JPEG, PNG, or WEBP image under 5MB.", HttpStatus.BAD_REQUEST),
    AVATAR_TOO_LARGE("MSG17", "Avatar must be a JPEG, PNG, or WEBP image under 5MB.", HttpStatus.PAYLOAD_TOO_LARGE),
    DUPLICATE_PHONE("MSG25", "Địa chỉ email hoặc số điện thoại này đã được đăng ký cho một tài khoản khác.", HttpStatus.CONFLICT),
    ADDRESS_NOT_FOUND("CRM_001", "Không tìm thấy địa chỉ giao hàng yêu cầu.", HttpStatus.NOT_FOUND),
    FORBIDDEN_ADDRESS_ACCESS("CRM_002", "Bạn không có quyền thao tác trên địa chỉ giao hàng này.", HttpStatus.FORBIDDEN),
    CUSTOMER_NOT_FOUND("CRM_003", "Không tìm thấy hồ sơ khách hàng tương ứng với tài khoản này.", HttpStatus.NOT_FOUND),
    AVATAR_UPLOAD_FAILED("CRM_005", "Tải lên ảnh đại diện thất bại. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CREDIT_PROFILE("CRM_006", "Hạn mức tín dụng phải lớn hơn 0 và kỳ hạn nợ phải từ 1 đến 30 ngày.", HttpStatus.BAD_REQUEST),
    CUSTOMER_NOT_ACTIVE_FOR_CREDIT("CRM_007", "Chỉ khách hàng đang hoạt động mới có thể được cấp tín dụng.", HttpStatus.CONFLICT),
    CREDIT_LIMIT_EXCEEDS_MAXIMUM("CRM_008", "Hạn mức tín dụng không được vượt quá 500.000.000 VNĐ.", HttpStatus.BAD_REQUEST),
    CREDIT_PROFILE_REQUEST_ALREADY_PENDING("CRM_009", "Khách hàng đã có yêu cầu cấp hạn mức tín dụng đang chờ duyệt.", HttpStatus.CONFLICT),
    CREDIT_PROFILE_REQUEST_NOT_FOUND("CRM_010", "Không tìm thấy yêu cầu cấp hạn mức tín dụng.", HttpStatus.NOT_FOUND),
    CREDIT_PROFILE_REQUEST_NOT_PENDING("CRM_011", "Yêu cầu cấp hạn mức tín dụng không còn ở trạng thái chờ duyệt.", HttpStatus.CONFLICT),
    CONTRACTOR_CREDIT_EVIDENCE_REQUIRED("CRM_012", "Cần tải ảnh CCCD/CMND, địa chỉ nhà riêng và địa chỉ công trình để cấp tín dụng cho thợ, nhà thầu hoặc khách dự án.", HttpStatus.BAD_REQUEST),
    INVALID_CREDIT_EVIDENCE("CRM_013", "Ảnh CCCD/CMND phải là JPEG, PNG hoặc WEBP và không quá 5MB.", HttpStatus.BAD_REQUEST),
    CREDIT_EVIDENCE_TOO_LARGE("CRM_014", "Ảnh CCCD/CMND phải là JPEG, PNG hoặc WEBP và không quá 5MB.", HttpStatus.PAYLOAD_TOO_LARGE),
    CREDIT_EVIDENCE_UPLOAD_FAILED("CRM_015", "Tải ảnh CCCD/CMND thất bại. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),
    CREDIT_EVIDENCE_NOT_FOUND("CRM_016", "Không tìm thấy ảnh CCCD/CMND đã tải lên.", HttpStatus.NOT_FOUND),
    CREDIT_EVIDENCE_STORAGE_UNAVAILABLE("CRM_017", "Kho lưu ảnh CCCD/CMND chưa sẵn sàng. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
