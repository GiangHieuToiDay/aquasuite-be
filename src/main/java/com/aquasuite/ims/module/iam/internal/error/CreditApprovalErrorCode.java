package com.aquasuite.ims.module.iam.internal.error;

import com.aquasuite.ims.share.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@RequiredArgsConstructor
public enum CreditApprovalErrorCode implements BaseErrorCode {
    APPROVER_NOT_OWNER("IAM_APR_001", "Only an active Store Owner can approve a customer credit profile.", HttpStatus.FORBIDDEN),
    INVALID_GRANT("IAM_APR_002", "Credit approval is invalid, expired, already used, or does not match this request.", HttpStatus.CONFLICT),
    GRANT_STORE_UNAVAILABLE("IAM_APR_003", "Credit approval service is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatusCode status;
}
