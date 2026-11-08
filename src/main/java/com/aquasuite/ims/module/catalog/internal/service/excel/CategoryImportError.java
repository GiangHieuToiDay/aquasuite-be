package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryImportError implements BaseErrorCode {
    REPORT_NOT_READY("CATIMP_001", "Report is not ready", HttpStatus.CONFLICT),
    FILE_TOO_LARGE("CATIMP_002", "Category workbook exceeds 5 MiB", HttpStatus.PAYLOAD_TOO_LARGE),
    UNAVAILABLE("CATIMP_003", "Category import storage is unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
