package com.aquasuite.ims.share.excel.exception;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExcelErrorCode implements BaseErrorCode {
    EMPTY_FILE(
            "EXL_001",
            "File Excel tải lên bị rỗng hoặc không đúng định dạng .xlsx",
            HttpStatus.BAD_REQUEST),
    INVALID_HEADER_SCHEMA(
            "EXL_002",
            "Cấu trúc cột tiêu đề ở dòng {0} không khớp với Template chuẩn. Cột bị sai: {1}",
            HttpStatus.BAD_REQUEST),
    NO_DATA_ROWS(
            "EXL_003", "File Excel không chứa dòng dữ liệu nào để import", HttpStatus.BAD_REQUEST),
    JOB_NOT_FOUND(
            "EXL_004",
            "Không tìm thấy phiên Import (Job ID: {0}) hoặc phiên đã hết hạn",
            HttpStatus.NOT_FOUND),
    FILE_READ_ERROR("EXL_005", "Lỗi đọc luồng file Excel: {0}", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
