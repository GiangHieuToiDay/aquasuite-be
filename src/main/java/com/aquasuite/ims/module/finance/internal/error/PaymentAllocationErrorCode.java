package com.aquasuite.ims.module.finance.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** Business failures for UC-63 receipt-to-electronic-invoice allocation. */
@Getter
@RequiredArgsConstructor
public enum PaymentAllocationErrorCode implements BaseErrorCode {
    RECEIPT_NOT_FOUND("ALC_001", "Không tìm thấy khoản thu cần phân bổ", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND("ALC_002", "Không tìm thấy đơn hàng của khoản thu", HttpStatus.NOT_FOUND),
    INVOICE_NOT_FOUND("ALC_003", "Không tìm thấy một hoặc nhiều hóa đơn cửa hàng cần phân bổ", HttpStatus.NOT_FOUND),
    RECEIPT_NOT_ALLOCATABLE("ALC_004", "Chỉ khoản thu RECEIPT đã xác minh và gắn với đơn mới được phân bổ", HttpStatus.BAD_REQUEST),
    ORDER_NOT_ELIGIBLE("ALC_005", "Đơn hàng không còn đủ điều kiện để phân bổ khoản thu", HttpStatus.CONFLICT),
    INVALID_ALLOCATION_TARGET("ALC_006", "Hóa đơn đích phải đã tích hợp hóa đơn điện tử, thuộc cùng đơn và khách hàng", HttpStatus.BAD_REQUEST),
    DUPLICATE_INVOICE_LINE("ALC_007", "Một hóa đơn chỉ được xuất hiện một lần trong yêu cầu phân bổ", HttpStatus.BAD_REQUEST),
    RECEIPT_BALANCE_EXCEEDED("ALC_008", "Tổng tiền phân bổ vượt số tiền chưa phân bổ của khoản thu", HttpStatus.BAD_REQUEST),
    INVOICE_BALANCE_EXCEEDED("ALC_009", "Số tiền phân bổ vượt số tiền còn nợ của hóa đơn", HttpStatus.BAD_REQUEST),
    RECEIPT_ALREADY_DIRECTLY_LINKED("ALC_010", "Khoản thu lịch sử đã gắn trực tiếp với hóa đơn điện tử cũ, không thể phân bổ lại", HttpStatus.CONFLICT),
    ALLOCATION_REQUEST_CONFLICT("ALC_011", "Mã yêu cầu phân bổ đã được dùng với dữ liệu khác", HttpStatus.CONFLICT),
    ALLOCATION_NOT_FOUND("ALC_012", "Không tìm thấy dòng phân bổ", HttpStatus.NOT_FOUND),
    ALLOCATION_ALREADY_REVERSED("ALC_013", "Dòng phân bổ đã được thu hồi", HttpStatus.CONFLICT),
    INVALID_REPLACEMENT_SETTLEMENT("ALC_014", "Không thể điều chuyển phân bổ theo kết quả đổi hoặc hủy đơn này", HttpStatus.CONFLICT),
    REFUND_NOT_VERIFIED("ALC_015", "Phần tiền hoàn chỉ được tất toán sau khi UC-64 xác minh khoản hoàn", HttpStatus.CONFLICT),
    ALLOCATION_NOT_REVERSIBLE("ALC_016", "Dòng điều chuyển theo UC-26 không thể thu hồi thủ công", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
