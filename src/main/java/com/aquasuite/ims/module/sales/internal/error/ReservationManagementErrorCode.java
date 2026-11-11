package com.aquasuite.ims.module.sales.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationManagementErrorCode implements BaseErrorCode {
    DECISION_NOT_FOUND("UC36_001", "Reservation release decision not found", HttpStatus.NOT_FOUND),
    INVALID_APPROVAL("UC36_002", "A completed Shop Owner approval for this order is required", HttpStatus.CONFLICT),
    REVIEW_REQUIRED("UC36_003", "Reservation or decision requires reconciliation before release", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
