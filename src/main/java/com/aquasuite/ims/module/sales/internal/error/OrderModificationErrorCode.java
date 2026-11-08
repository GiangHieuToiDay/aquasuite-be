package com.aquasuite.ims.module.sales.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum OrderModificationErrorCode implements BaseErrorCode {
    REQUEST_NOT_FOUND("ORDER_MOD_001", "Order modification request was not found.", HttpStatus.NOT_FOUND),
    INVALID_REQUEST_STATE("ORDER_MOD_002", "Order modification request is not in a reviewable state.", HttpStatus.CONFLICT),
    ORDER_NOT_ELIGIBLE("ORDER_MOD_003", "Only Processing orders can be reviewed for modification or cancellation.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatusCode status;
}
