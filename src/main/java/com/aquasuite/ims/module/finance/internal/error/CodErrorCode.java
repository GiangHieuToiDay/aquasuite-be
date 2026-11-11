package com.aquasuite.ims.module.finance.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CodErrorCode implements BaseErrorCode {
    INVALID("COD_001", "Dữ liệu khai thu hoặc bàn giao COD không hợp lệ", HttpStatus.BAD_REQUEST),
    CONFLICT("COD_002", "Số dư hoặc trạng thái COD đã thay đổi; tải lại để đối soát", HttpStatus.CONFLICT),
    RECONCILE_FIRST("COD_003", "Cần đối soát COD trước khi xử lý công nợ khách", HttpStatus.CONFLICT),
    DUPLICATE_KEY("COD_004", "Mã yêu cầu đã được dùng với dữ liệu khác", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
