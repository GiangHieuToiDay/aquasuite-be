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
    CUSTOMER_NOT_FOUND("SVD_008", "Không tìm thấy khách hàng: {0}", HttpStatus.BAD_REQUEST),
    WARRANTY_POLICY_NOT_FOUND("WPO_001", "Không tìm thấy chính sách bảo hành: {0}", HttpStatus.NOT_FOUND),
    WARRANTY_POLICY_BRAND_NOT_FOUND("WPO_002", "Không tìm thấy hãng đã chọn: {0}", HttpStatus.BAD_REQUEST),
    WARRANTY_POLICY_CATEGORY_NOT_FOUND("WPO_003", "Không tìm thấy nhóm hàng đã chọn: {0}", HttpStatus.BAD_REQUEST),
    DUPLICATE_ACTIVE_WARRANTY_POLICY("WPO_004", "Đã có chính sách đang áp dụng cho hãng và nhóm hàng này: {0}", HttpStatus.CONFLICT),
    WARRANTY_POLICY_IN_USE("WPO_005", "Không thể xóa chính sách vì đang có {0} biến thể áp dụng", HttpStatus.CONFLICT),
    WARRANTY_NOT_FOUND("SVD_009", "Không tìm thấy thẻ bảo hành: {0}", HttpStatus.NOT_FOUND),
    WARRANTY_SERIAL_REQUIRED("SVD_010", "Sản phẩm quản lý theo serial bắt buộc nhập số serial.", HttpStatus.BAD_REQUEST),
    WARRANTY_DUPLICATE_ACTIVE_SERIAL("SVD_011", "Số serial {0} đang có thẻ bảo hành còn hiệu lực cho sản phẩm này.", HttpStatus.CONFLICT),
    WARRANTY_POLICY_NOT_APPLICABLE("SVD_012", "Sản phẩm hoặc chính sách bảo hành không áp dụng cấp thẻ bảo hành (thời hạn phải > 0 tháng và ở trạng thái ACTIVE).", HttpStatus.BAD_REQUEST),
    WARRANTY_ALREADY_VOIDED("SVD_013", "Thẻ bảo hành {0} đã bị hủy trước đó.", HttpStatus.CONFLICT),
    WARRANTY_VOID_REASON_REQUIRED("SVD_014", "Vui lòng nhập lý do hủy thẻ bảo hành.", HttpStatus.BAD_REQUEST),
    WARRANTY_HAS_OPEN_JOB("SVD_015", "Không thể hủy thẻ bảo hành khi đang có phiếu sửa chữa bảo hành chưa hoàn tất.", HttpStatus.CONFLICT),
    WARRANTY_ORDER_ITEM_NOT_FOUND("SVD_016", "Không tìm thấy dòng sản phẩm trong đơn hàng: {0}", HttpStatus.NOT_FOUND),
    WARRANTY_ORDER_NOT_ELIGIBLE("SVD_017", "Đơn hàng ở trạng thái {0} không đủ điều kiện cấp thẻ bảo hành.", HttpStatus.CONFLICT),
    WARRANTY_QUANTITY_LIMIT_EXCEEDED("SVD_018", "Số lượng thẻ bảo hành đang hiệu lực đã đạt giới hạn số lượng mua trên dòng đơn hàng.", HttpStatus.CONFLICT),
    WARRANTY_FORBIDDEN("SVD_019", "Bạn không có quyền truy cập thẻ bảo hành này.", HttpStatus.FORBIDDEN),
    WARRANTY_VARIANT_NOT_FOUND("SVD_020", "Không tìm thấy phiên bản sản phẩm (SKU): {0}", HttpStatus.BAD_REQUEST),
    WARRANTY_TICKET_ALREADY_OPEN("SVD_021", "Thẻ bảo hành đang có yêu cầu bảo hành hoặc công việc kỹ thuật chưa hoàn tất (Mã: {0}).", HttpStatus.CONFLICT),
    WARRANTY_RECORD_VOIDED("SVD_022", "Thẻ bảo hành đã bị vô hiệu hóa, không thể yêu cầu bảo hành.", HttpStatus.CONFLICT),
    WARRANTY_TICKET_NOT_FOUND("SVD_023", "Không tìm thấy yêu cầu bảo hành: {0}", HttpStatus.NOT_FOUND),
    INVALID_MEDIA_FILE("SVD_024", "File đính kèm không hợp lệ: {0}", HttpStatus.BAD_REQUEST),
    MEDIA_COUNT_LIMIT_EXCEEDED("SVD_025", "Vượt quá số lượng file đính kèm tối đa (tối đa 5 ảnh và 1 video).", HttpStatus.BAD_REQUEST),
    INVALID_PREFERRED_DATE("SVD_026", "Ngày hẹn không hợp lệ (phải từ hôm nay đến tối đa 60 ngày tới).", HttpStatus.BAD_REQUEST),
    WARRANTY_TICKET_FORBIDDEN("SVD_027", "Không tìm thấy yêu cầu bảo hành: {0}", HttpStatus.NOT_FOUND),
    WARRANTY_TICKET_NOT_CANCELLABLE("SVD_028", "Chỉ có thể hủy yêu cầu bảo hành khi ở trạng thái Chờ tiếp nhận (NEW).", HttpStatus.CONFLICT),
    MEDIA_UPLOAD_FAILED("SVD_029", "Tải file đính kèm lên Cloudinary thất bại. Vui lòng thử lại sau.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
