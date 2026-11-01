package com.aquasuite.ims.module.finance.internal.payload.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DebtCallResultRequest(
        @NotNull CallResult callResult,
        LocalDate promisedPaymentDate,
        @Size(max = 500) String note) {
    public enum CallResult { PROMISED_TO_PAY, NEEDS_REVIEW, NO_ANSWER, NUMBER_UNREACHABLE }
}
