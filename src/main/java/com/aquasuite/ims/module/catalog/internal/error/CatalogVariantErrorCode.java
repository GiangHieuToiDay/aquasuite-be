package com.aquasuite.ims.module.catalog.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CatalogVariantErrorCode implements BaseErrorCode {
    PRODUCT_NOT_FOUND("SKU_001", "Không tìm thấy sản phẩm gốc", HttpStatus.NOT_FOUND),
    VARIANT_NOT_FOUND("SKU_002", "Không tìm thấy SKU", HttpStatus.NOT_FOUND),
    DUPLICATE_SKU("SKU_003", "Phát hiện mã SKU bị trùng lặp. Mỗi biến thể phải có một mã SKU duy nhất.", HttpStatus.CONFLICT),
    PRICE_WARNING_ACK_REQUIRED("SKU_004", "Giá bán thấp hơn giá vốn. Vui lòng xác nhận để tiếp tục.", HttpStatus.CONFLICT),
    INVALID_STATUS("SKU_005", "Trạng thái biến thể không hợp lệ", HttpStatus.BAD_REQUEST),
    RETAIL_PRICE_REQUIRED("SKU_006", "Cần có giá bán niêm yết để kích hoạt SKU", HttpStatus.BAD_REQUEST),
    DELETE_BLOCKED("SKU_007", "SKU đã có phát sinh hoặc còn tồn; hãy dùng Ngừng kinh doanh thay thế", HttpStatus.CONFLICT),
    INVALID_DELETE_CONFIRMATION("SKU_008", "Cần xác nhận xóa vĩnh viễn SKU", HttpStatus.BAD_REQUEST);
    private final String code;
    private final String message;
    private final HttpStatus status;
}
