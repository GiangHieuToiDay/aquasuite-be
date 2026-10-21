package com.aquasuite.ims.module.catalog.internal.exception;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CatalogErrorCode implements BaseErrorCode {
    PRODUCT_NOT_FOUND("CAT_001", "Không tìm thấy sản phẩm: {0}", HttpStatus.NOT_FOUND),
    PRODUCT_CODE_ALREADY_EXISTS("CAT_002", "Product Code must be unique. This code is already in use.", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND("CAT_003", "Không tìm thấy danh mục với ID: {0}", HttpStatus.NOT_FOUND),
    BRAND_NOT_FOUND("CAT_004", "Không tìm thấy thương hiệu với ID: {0}", HttpStatus.NOT_FOUND),
    SKU_ALREADY_EXISTS("CAT_005", "Mã SKU biến thể đã tồn tại trong hệ thống: {0}", HttpStatus.CONFLICT),
    VARIANT_NOT_FOUND("CAT_006", "Không tìm thấy biến thể sản phẩm: {0}", HttpStatus.NOT_FOUND),
    DUPLICATE_SKU_IN_REQUEST("CAT_007", "Mã SKU bị lặp lại trong danh sách biến thể gửi lên: {0}", HttpStatus.BAD_REQUEST),
    PRODUCT_HAS_NO_VARIANTS("CAT_008", "Sản phẩm phải có ít nhất một biến thể hoạt động", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
