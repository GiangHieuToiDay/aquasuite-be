package com.aquasuite.ims.module.sales.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectDepositErrorCode implements BaseErrorCode {
    ORDER_NOT_FOUND("SALES_DEPOSIT_001", "Order not found", HttpStatus.NOT_FOUND),
    ORDER_NOT_ELIGIBLE("SALES_DEPOSIT_002", "A confirmed order with approved prices is required", HttpStatus.CONFLICT),
    INVALID_REQUEST("SALES_DEPOSIT_003", "Invalid deposit or reservation request", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_CONFLICT("SALES_DEPOSIT_004", "Request ID was already used with different input", HttpStatus.CONFLICT),
    STALE_VERSION("SALES_DEPOSIT_005", "Delivery schedule changed; reload before saving", HttpStatus.CONFLICT),
    INTEGRATION_UNAVAILABLE("SALES_DEPOSIT_006", "Dependent module is unavailable; retry without recording payment again", HttpStatus.SERVICE_UNAVAILABLE),
    RESERVATION_REQUEST_REQUIRED("SALES_DEPOSIT_007", "Select the order lines and quantities to reserve before recording a deposit", HttpStatus.CONFLICT),
    RESERVATION_SELECTION_CONFLICT("SALES_DEPOSIT_008", "Reservation scope or agreed order prices changed; review the request", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
