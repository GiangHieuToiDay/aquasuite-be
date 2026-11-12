package com.aquasuite.ims.module.inventory.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements BaseErrorCode {
    INVALID("INV_RES_001", "Invalid reservation command", HttpStatus.BAD_REQUEST),
    CONFLICT("INV_RES_002", "Reservation request or source-line target conflicts", HttpStatus.CONFLICT),
    NOT_FOUND("INV_RES_003", "Reservation not found", HttpStatus.NOT_FOUND),
    EXCESS_RELEASE("INV_RES_004", "Release exceeds remaining reserved quantity", HttpStatus.CONFLICT),
    INCONSISTENT("INV_RES_005", "Reservation and inventory totals require reconciliation", HttpStatus.CONFLICT);
    private final String code;
    private final String message;
    private final HttpStatus status;
}
