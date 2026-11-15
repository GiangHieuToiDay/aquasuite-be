package com.aquasuite.ims.module.sales.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SalesErrorCode implements BaseErrorCode {

    INSUFFICIENT_STOCK("SALES_001", "Số lượng tồn kho khả dụng không đủ cho mặt hàng: {0}", HttpStatus.BAD_REQUEST),
    CREDIT_LIMIT_EXCEEDED("SALES_002", "Credit limit exceeded or overdue balance detected. Order on credit is blocked.", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND("SALES_003", "Không tìm thấy đơn hàng yêu cầu", HttpStatus.NOT_FOUND),
    CUSTOMER_NOT_FOUND("SALES_006", "Không tìm thấy khách hàng trong hệ thống", HttpStatus.NOT_FOUND),
    SKU_NOT_FOUND("SALES_007", "Không tìm thấy thông tin biến thể SKU: {0}", HttpStatus.NOT_FOUND),
    SKU_NOT_ACTIVE("SALES_008", "Biến thể SKU {0} không ở trạng thái hoạt động", HttpStatus.BAD_REQUEST),
    COMBO_NOT_FOUND("SALES_009", "Không tìm thấy thông tin gói combo: {0}", HttpStatus.NOT_FOUND),
    ORDER_ITEMS_EMPTY("SALES_010", "Đơn hàng phải chứa ít nhất một sản phẩm hoặc combo", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATUS("SALES_011", "Trạng thái thanh toán không hợp lệ: {0}", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_PAYMENT_METHOD("SALES_012", "Phương thức thanh toán không được hỗ trợ", HttpStatus.BAD_REQUEST),
    ACTOR_REQUIRED("SALES_013", "Cần xác định nhân viên tạo đơn", HttpStatus.UNAUTHORIZED),
    ALREADY_CONVERTED_TO_ORDER("SALES_014", "Báo giá {0} đã được chuyển đổi thành đơn hàng: {1}", HttpStatus.CONFLICT),
    QUOTATION_NOT_ACCEPTED_OR_APPROVED("SALES_015", "Báo giá phải ở trạng thái ĐÃ DUYỆT (APPROVED) hoặc ĐÃ CHỐT (ACCEPTED) mới được tạo đơn hàng, trạng thái hiện tại: {0}", HttpStatus.BAD_REQUEST),
    QUOTATION_NOT_FOUND("SALES_016", "Không tìm thấy thông tin báo giá: {0}", HttpStatus.NOT_FOUND),
    ORDER_NOT_MODIFIABLE("SALES_017", "Chỉ có thể cập nhật thông tin khi đơn hàng đang ở trạng thái Đang xử lý (PROCESSING)", HttpStatus.BAD_REQUEST),
    ORDER_CANNOT_COMPLETE("SALES_018", "Đơn hàng chỉ có thể hoàn tất khi đang ở trạng thái Đang xử lý (PROCESSING) và đã thanh toán đủ (PAID)", HttpStatus.BAD_REQUEST),
    INVOICE_NOT_FOUND("SALES_019", "Không tìm thấy hóa đơn yêu cầu: {0}", HttpStatus.NOT_FOUND),
    INVOICE_NOT_EDITABLE("SALES_020", "Hóa đơn đã ở trạng thái {0}, không thể chỉnh sửa hoặc hủy trực tiếp", HttpStatus.CONFLICT),
    INVOICE_ITEMS_EMPTY("SALES_021", "Hóa đơn phải chứa ít nhất một dòng sản phẩm với số lượng lớn hơn 0", HttpStatus.BAD_REQUEST),
    INVOICE_MODIFICATION_BLOCKED_BY_UC26("SALES_022", "Đơn hàng đang có yêu cầu điều chỉnh/hủy (UC-26) chờ xử lý, tạm khóa chỉnh sửa hoặc phát hành hóa đơn", HttpStatus.CONFLICT),
    INVOICE_TOTAL_BELOW_PAID("SALES_023", "Tổng giá trị đơn hàng mới ({0}) không được nhỏ hơn số tiền khách đã thanh toán xác nhận ({1})", HttpStatus.CONFLICT),
    INVOICE_SPLIT_INVALID("SALES_024", "Dữ liệu chia đợt hóa đơn không hợp lệ: {0}", HttpStatus.BAD_REQUEST),
    INVOICE_CANNOT_DELETE_ONLY_BATCH("SALES_025", "Không thể xóa hóa đơn duy nhất của đơn hàng", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
