package com.aquasuite.ims.exception.sales;

import com.aquasuite.ims.exception.BaseErrorCode;
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
    INVOICE_CANNOT_DELETE_ONLY_BATCH("SALES_025", "Không thể xóa hóa đơn duy nhất của đơn hàng", HttpStatus.BAD_REQUEST),
    CART_ITEM_NOT_FOUND("SALES_026", "Không tìm thấy sản phẩm trong giỏ hàng", HttpStatus.NOT_FOUND),
    INVALID_CART_QUANTITY("SALES_027", "Số lượng sản phẩm trong giỏ hàng phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    CART_EMPTY_FOR_CHECKOUT("SALES_028", "Giỏ hàng đang trống, không thể đặt hàng trực tuyến", HttpStatus.BAD_REQUEST),
    DELIVERY_ADDRESS_REQUIRED("SALES_029", "Vui lòng chọn hoặc nhập địa chỉ giao hàng hợp lệ", HttpStatus.BAD_REQUEST),
    ONLINE_PAYMENT_METHOD_INVALID("SALES_030", "Đơn hàng trực tuyến chỉ hỗ trợ Thanh toán chuyển khoản (PayOS) hoặc COD", HttpStatus.BAD_REQUEST),
    PAYOS_CONFIG_MISSING("SALES_031", "Chưa cấu hình thông tin kết nối PayOS (Client ID / API Key / Checksum Key)", HttpStatus.BAD_REQUEST),
    PAYOS_API_ERROR("SALES_032", "Lỗi kết nối cổng thanh toán PayOS: {0}", HttpStatus.BAD_GATEWAY),
    ORDER_NOT_PAYABLE_VIA_PAYOS("SALES_033", "Đơn hàng không hỗ trợ hoặc đã hoàn tất thanh toán PayOS", HttpStatus.BAD_REQUEST),
    INVOICE_NOT_ISSUED("SALES_034", "Hóa đơn phải ở trạng thái ISSUED hoặc DISPATCHED mới có thể liên kết hóa đơn điện tử", HttpStatus.CONFLICT),
    ELECTRONIC_INVOICE_ALREADY_LINKED("SALES_035", "Hóa đơn đã được liên kết với một hóa đơn điện tử khác", HttpStatus.CONFLICT),
    ORDER_NOT_PENDING_CONFIRMATION("SALES_036", "Thao tác chỉ áp dụng cho đơn hàng đang ở trạng thái Chờ xác nhận (PENDING_CONFIRMATION), trạng thái hiện tại: {0}", HttpStatus.BAD_REQUEST),
    ONLINE_ORDER_PAYOS_UNPAID("SALES_037", "Đơn hàng thanh toán trực tuyến qua PayOS chưa hoàn tất thanh toán. Vui lòng kiểm tra/đồng bộ thanh toán trước khi xác nhận đơn", HttpStatus.BAD_REQUEST),
    ORDER_REJECT_REASON_REQUIRED("SALES_038", "Vui lòng nhập lý do từ chối hoặc hủy đơn hàng", HttpStatus.BAD_REQUEST),
    CUSTOMER_NOT_ACTIVE("QUOTATION_002", "Khách hàng không ở trạng thái hoạt động: {0}", HttpStatus.BAD_REQUEST),
    SKU_INACTIVE("QUOTATION_004", "Biến thể sản phẩm đã ngừng kinh doanh: {0}", HttpStatus.BAD_REQUEST),
    DUPLICATE_SKU_ITEM("QUOTATION_005", "Biến thể sản phẩm {0} chỉ được xuất hiện một lần trong danh sách dòng báo giá", HttpStatus.BAD_REQUEST),
    INVALID_UNIT_QUANTITY("QUOTATION_006", "Số lượng đặt ({0}) không hợp lệ theo quy cách đơn vị {1}. Đơn vị rời rạc bắt buộc là số nguyên dương", HttpStatus.BAD_REQUEST),
    INVALID_VALID_UNTIL_DATE("QUOTATION_007", "Hạn hiệu lực báo giá ({0}) không được trước ngày lập báo giá ({1})", HttpStatus.BAD_REQUEST),
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
