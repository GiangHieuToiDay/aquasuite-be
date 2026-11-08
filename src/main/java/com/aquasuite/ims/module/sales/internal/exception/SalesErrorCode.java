package com.aquasuite.ims.module.sales.internal.exception;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SalesErrorCode implements BaseErrorCode {
    CUSTOMER_NOT_FOUND("QUOTATION_001", "Không tìm thấy thông tin khách hàng: {0}", HttpStatus.NOT_FOUND),
    CUSTOMER_NOT_ACTIVE("QUOTATION_002", "Khách hàng không ở trạng thái hoạt động: {0}", HttpStatus.BAD_REQUEST),
    SKU_NOT_FOUND("QUOTATION_003", "Không tìm thấy biến thể sản phẩm: {0}", HttpStatus.NOT_FOUND),
    SKU_INACTIVE("QUOTATION_004", "Biến thể sản phẩm đã ngừng kinh doanh: {0}", HttpStatus.BAD_REQUEST),
    DUPLICATE_SKU_ITEM("QUOTATION_005", "Biến thể sản phẩm {0} chỉ được xuất hiện một lần trong danh sách dòng báo giá", HttpStatus.BAD_REQUEST),
    INVALID_UNIT_QUANTITY("QUOTATION_006", "Số lượng đặt ({0}) không hợp lệ theo quy cách đơn vị {1}. Đơn vị rời rạc bắt buộc là số nguyên dương", HttpStatus.BAD_REQUEST),
    INVALID_VALID_UNTIL_DATE("QUOTATION_007", "Hạn hiệu lực báo giá ({0}) không được trước ngày lập báo giá ({1})", HttpStatus.BAD_REQUEST),
    QUOTATION_NOT_FOUND("QUOTATION_008", "Không tìm thấy báo giá: {0}", HttpStatus.NOT_FOUND),
    QUOTATION_STATE_LOCKED("QUOTATION_009", "Báo giá đã khóa ở trạng thái {0}, vui lòng tạo Phiên bản mới (Revision) để chỉnh sửa", HttpStatus.BAD_REQUEST),
    INVALID_STATUS_TRANSITION("QUOTATION_010", "Không thể thực hiện thao tác khi báo giá đang ở trạng thái {0}", HttpStatus.BAD_REQUEST),
    DISCOUNT_WITHIN_LIMIT("QUOTATION_011", "Báo giá nằm trong hạn mức chiết khấu cho phép ({0}%), bạn có thể gửi trực tiếp mà không cần xin phê duyệt", HttpStatus.BAD_REQUEST),
    APPROVAL_NOTE_REQUIRED("QUOTATION_012", "Lý do phê duyệt/từ chối không được để trống", HttpStatus.BAD_REQUEST),
    REVISION_ALREADY_EXISTS("QUOTATION_013", "Đã tồn tại một phiên bản sửa đổi đang xử lý cho mã báo giá {0}, vui lòng hoàn tất phiên bản đó trước", HttpStatus.CONFLICT),
    ONLY_DRAFT_CAN_BE_DELETED("QUOTATION_014", "Chỉ được phép xóa báo giá bản gốc đang ở trạng thái Nháp (Rev 1)", HttpStatus.BAD_REQUEST),
    LOST_REASON_REQUIRED("QUOTATION_015", "Lý do thất bại không được để trống", HttpStatus.BAD_REQUEST),
    QUOTATION_ITEMS_EMPTY("QUOTATION_016", "Báo giá phải có ít nhất một dòng sản phẩm trước khi thực hiện thao tác này", HttpStatus.BAD_REQUEST),
    FORBIDDEN_QUOTATION_ACCESS("QUOTATION_017", "Bạn không có quyền chỉnh sửa báo giá của người khác", HttpStatus.FORBIDDEN),
    QUOTATION_NOT_SENT("QUOTATION_018", "Thao tác chỉ áp dụng cho báo giá đã gửi (SENT), đã duyệt (APPROVED) hoặc mất dự án (LOST), trạng thái hiện tại: {0}", HttpStatus.BAD_REQUEST),
    DISCOUNT_LIMIT_EXCEEDED("QUOTATION_019", "Báo giá có chiết khấu ({0}%) vượt quá hạn mức của nhân viên ({1}%), bắt buộc phải trình phê duyệt trước khi phát hành", HttpStatus.BAD_REQUEST),
    INVALID_COMBO("QUOTATION_020", "Combo không hợp lệ hoặc không còn kinh doanh: {0}", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
