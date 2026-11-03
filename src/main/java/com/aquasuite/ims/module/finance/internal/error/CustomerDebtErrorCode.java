package com.aquasuite.ims.module.finance.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum CustomerDebtErrorCode implements BaseErrorCode {
    CUSTOMER_NOT_FOUND("DEBT_001", "Customer debt profile was not found.", HttpStatus.NOT_FOUND),
    CREDIT_NOT_AVAILABLE("MSG63", "Credit limit exceeded or overdue balance detected. Order on credit is blocked.", HttpStatus.CONFLICT),
    CUSTOMER_BLOCKED("DEBT_002", "Customer is blocked and cannot create a new order.", HttpStatus.CONFLICT),
    BLACKLISTED("DEBT_003", "Customer has overdue debt over 60 days and is on the debt blacklist. New credit orders are blocked.", HttpStatus.CONFLICT),
    OVERRIDE_NOT_ALLOWED("DEBT_004", "Credit override is not allowed for this order.", HttpStatus.CONFLICT),
    INVALID_CREDIT_REQUEST("DEBT_005", "Credit is available only for eligible counter orders.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatusCode status;
}
