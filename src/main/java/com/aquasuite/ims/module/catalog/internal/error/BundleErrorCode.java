package com.aquasuite.ims.module.catalog.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;

import lombok.*;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BundleErrorCode implements BaseErrorCode {
    INVALID_COMPONENT(
            "CATALOG_BUNDLE_001", "Thành phần combo không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_DEFINITION(
            "CATALOG_BUNDLE_002", "Giá hoặc thông tin combo không hợp lệ", HttpStatus.BAD_REQUEST),
    STALE_VERSION(
            "CATALOG_BUNDLE_003", "Combo đã được thay đổi, vui lòng tải lại", HttpStatus.CONFLICT),
    INVALID_STATE(
            "CATALOG_BUNDLE_006",
            "Trạng thái combo không cho phép thao tác này",
            HttpStatus.CONFLICT);
    private final String code;
    private final String message;
    private final HttpStatus status;
}
