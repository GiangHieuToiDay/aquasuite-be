package com.aquasuite.ims.module.servicedesk.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi nghiệp vụ của module Servicedesk (FE-13 - Công việc kỹ thuật, UC-59/60/61).
 */
@Getter
@RequiredArgsConstructor
public enum ServicedeskErrorCode implements BaseErrorCode {

    TECHNICAL_JOB_NOT_FOUND("SVD_001", "Không tìm thấy công việc kỹ thuật: {0}", HttpStatus.NOT_FOUND),
    TECHNICAL_JOB_NOT_OWNED("SVD_002", "Bạn không được phân công công việc kỹ thuật này", HttpStatus.FORBIDDEN),
    INVALID_JOB_STATUS_TRANSITION("SVD_003", "Không thể chuyển trạng thái công việc từ {0} sang {1}", HttpStatus.CONFLICT),
    REJECTION_REASON_REQUIRED("SVD_004", "Phải nhập lý do khi từ chối công việc kỹ thuật", HttpStatus.BAD_REQUEST),
    JOB_RESULT_REQUIRED("SVD_005", "Phải nhập kết quả thực hiện khi hoàn tất công việc kỹ thuật", HttpStatus.BAD_REQUEST),
    INVALID_TECHNICIAN("SVD_006", "Người được phân công không hợp lệ hoặc không phải nhân viên kỹ thuật: {0}", HttpStatus.BAD_REQUEST),
    JOB_NOT_ASSIGNABLE("SVD_007", "Chỉ có thể phân công công việc đang ở trạng thái chờ tiếp nhận (ASSIGNED) hoặc đã bị từ chối (REJECTED). Trạng thái hiện tại: {0}", HttpStatus.CONFLICT),
    CUSTOMER_NOT_FOUND("SVD_008", "Không tìm thấy khách hàng: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
