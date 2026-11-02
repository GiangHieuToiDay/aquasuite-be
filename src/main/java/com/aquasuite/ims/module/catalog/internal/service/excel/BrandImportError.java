package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BrandImportError implements BaseErrorCode {
    REPORT_NOT_READY("BRIMP_001", "Report is not ready", HttpStatus.CONFLICT),
    FILE_TOO_LARGE("BRIMP_002", "Brand workbook exceeds 5 MiB", HttpStatus.PAYLOAD_TOO_LARGE),
    UNAVAILABLE("BRIMP_003", "Brand import storage is unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
