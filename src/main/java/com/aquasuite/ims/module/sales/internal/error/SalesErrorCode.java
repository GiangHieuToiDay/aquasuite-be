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
    ACTOR_REQUIRED("SALES_013", "Cần xác định nhân viên tạo đơn", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
