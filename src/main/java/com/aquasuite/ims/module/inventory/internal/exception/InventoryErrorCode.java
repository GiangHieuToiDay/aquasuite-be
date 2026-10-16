package com.aquasuite.ims.module.inventory.internal.exception;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements BaseErrorCode {
    INVENTORY_NOT_FOUND("INV_001", "Không tìm thấy thông tin tồn kho của sản phẩm: {0}", HttpStatus.NOT_FOUND),
    INSUFFICIENT_AVAILABLE_STOCK("INV_002", "Số lượng tồn khả dụng không đủ. Cần: {0}, Khả dụng: {1}", HttpStatus.BAD_REQUEST),
    NEGATIVE_ON_HAND_STOCK("INV_003", "Thao tác làm âm tồn kho thực tế. Hiện tại: {0}, Điều chỉnh: {1}", HttpStatus.BAD_REQUEST),
    STOCK_COUNT_NOT_FOUND("INV_004", "Không tìm thấy phiếu kiểm kho: {0}", HttpStatus.NOT_FOUND),
    INVALID_STOCK_COUNT_STATUS("INV_005", "Trạng thái phiếu kiểm kho không hợp lệ: {0}", HttpStatus.BAD_REQUEST),
    SUPPLIER_NOT_FOUND("INV_006", "Không tìm thấy nhà cung cấp: {0}", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
