package com.aquasuite.ims.exception.fulfillment;

import com.aquasuite.ims.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FulfillmentErrorCode implements BaseErrorCode {
    COD_LIMIT_EXCEEDED("FUL_007", "Tiền COD vượt số tiền còn được thu của đơn hàng. Tải lại số dư và nhập số tiền nhỏ hơn hoặc bằng hạn mức; đơn đã thanh toán đủ phải nhập COD bằng 0.", HttpStatus.CONFLICT),
    DRAFT_NOT_EDITABLE("FUL_008", "Chỉ được sửa đợt giao đang ở bản nháp và chưa xác nhận xuất kho.", HttpStatus.CONFLICT),
    INVALID_SOURCE_QUANTITY("FUL_009", "Dòng hàng hoặc số lượng giao không hợp lệ: không được vượt số lượng còn lại của đơn hàng và hóa đơn đã phát hành.", HttpStatus.CONFLICT),
    ORDER_NOT_ELIGIBLE("FUL_010", "Đơn hàng phải đang xử lý, không có yêu cầu sửa đổi chưa giải quyết và không có đợt giao cũ cần đối chiếu.", HttpStatus.CONFLICT),
    RESERVATION_REVIEW_REQUIRED("FUL_006", "Hàng giữ của dòng và tồn khả dụng chưa đủ để xuất. Mở Cọc & hàng giữ của đơn để kiểm tra và giữ thêm hàng. Lượng giữ tổng chưa rõ thuộc đơn nào phải được đối chiếu; không tự lấy hàng giữ của đơn khác.", HttpStatus.CONFLICT),
    INVALID_STATE("FUL_001", "Delivery cannot perform this operation in its current state", HttpStatus.CONFLICT),
    INVALID_SHIPPER("FUL_002", "Select an active shipper", HttpStatus.BAD_REQUEST),
    INVALID_PHOTO("FUL_003", "Provide 1 to 5 valid JPEG, PNG or WebP images, each at most 5 MiB", HttpStatus.BAD_REQUEST),
    STORAGE_UNAVAILABLE("FUL_004", "Delivery photo storage is not configured or unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    SERIAL_DISPATCH_UNSUPPORTED("FUL_005", "Chưa hỗ trợ xuất giao hàng quản lý serial. Vui lòng bỏ dòng serial khỏi đợt giao này.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
