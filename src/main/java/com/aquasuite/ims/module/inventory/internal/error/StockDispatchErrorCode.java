package com.aquasuite.ims.module.inventory.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockDispatchErrorCode implements BaseErrorCode {
    INCONSISTENT_HOLDS("INV_DISPATCH_001", "Lượng giữ trong kho nhỏ hơn sổ giữ hàng. Cần đối chiếu tồn kho trước khi xuất.", HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK("INV_DISPATCH_002", "Không đủ tồn khả dụng để xuất; hàng giữ của đơn khác không được sử dụng.", HttpStatus.CONFLICT),
    SERIAL_NOT_SUPPORTED("INV_DISPATCH_003", "SKU yêu cầu serial chưa hỗ trợ xác nhận xuất trong luồng này.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
