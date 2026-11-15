package com.aquasuite.ims.module.fulfillment.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FulfillmentErrorCode implements BaseErrorCode {
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
