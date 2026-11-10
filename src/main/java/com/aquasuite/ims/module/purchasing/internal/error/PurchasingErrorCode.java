package com.aquasuite.ims.module.purchasing.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PurchasingErrorCode implements BaseErrorCode {

    PO_NOT_FOUND("PO_001", "Không tìm thấy đơn mua hàng yêu cầu", HttpStatus.NOT_FOUND),
    SUPPLIER_NOT_FOUND("PO_002", "Không tìm thấy nhà cung cấp: {0}", HttpStatus.NOT_FOUND),
    SUPPLIER_NOT_ACTIVE("PO_003", "Nhà cung cấp {0} không còn ở trạng thái hoạt động", HttpStatus.BAD_REQUEST),
    SKU_NOT_FOUND("PO_004", "Không tìm thấy SKU: {0}", HttpStatus.NOT_FOUND),
    SKU_NOT_ACTIVE("PO_005", "SKU {0} không ở trạng thái hoạt động", HttpStatus.BAD_REQUEST),
    PO_ITEMS_EMPTY("PO_006", "Đơn mua hàng phải có ít nhất một dòng sản phẩm", HttpStatus.BAD_REQUEST),
    PO_CANNOT_EDIT("PO_007", "Chỉ có thể chỉnh sửa đơn mua hàng ở trạng thái nháp (DRAFT)", HttpStatus.BAD_REQUEST),
    PO_VERSION_CONFLICT("PO_008", "Đơn mua hàng đã bị thay đổi bởi người khác, vui lòng tải lại trang", HttpStatus.CONFLICT),
    PO_CONFIRM_MISSING_PRICE("PO_009", "Dòng hàng SKU {0} chưa có đơn giá nhập, không thể xác nhận đơn", HttpStatus.BAD_REQUEST),
    PO_CANNOT_CONFIRM_STATUS("PO_010", "Chỉ có thể xác nhận đơn mua hàng đang ở trạng thái DRAFT", HttpStatus.BAD_REQUEST),
    PO_CANNOT_CANCEL_RECEIPT_EXISTS("PO_011", "Không thể hủy PO đã có phiếu nhập kho được xác nhận hoặc đang có phiếu nhập nháp", HttpStatus.BAD_REQUEST),
    PO_CANNOT_CANCEL_STATUS("PO_012", "Chỉ có thể hủy đơn mua hàng ở trạng thái DRAFT hoặc CONFIRMED", HttpStatus.BAD_REQUEST),
    PO_CANNOT_CLOSE_STATUS("PO_013", "Chỉ có thể đóng PO ở trạng thái PARTIALLY_RECEIVED", HttpStatus.BAD_REQUEST),
    PO_CANNOT_CLOSE_DRAFT_RECEIPT("PO_014", "Không thể đóng PO khi còn phiếu nhập kho ở trạng thái nháp (DRAFT)", HttpStatus.BAD_REQUEST),
    CANCEL_REASON_REQUIRED("PO_015", "Bắt buộc phải nhập lý do hủy đơn", HttpStatus.BAD_REQUEST),
    CLOSE_REASON_REQUIRED("PO_016", "Bắt buộc phải nhập lý do đóng PO", HttpStatus.BAD_REQUEST),
    INVALID_QUANTITY("PO_017", "Số lượng đặt cho SKU {0} phải lớn hơn 0 và đúng quy cách đơn vị", HttpStatus.BAD_REQUEST),
    INVALID_UNIT_COST("PO_018", "Đơn giá nhập cho SKU {0} phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST),
    DUPLICATE_SKU_IN_PO("PO_019", "SKU {0} đã xuất hiện trong đơn mua hàng", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}