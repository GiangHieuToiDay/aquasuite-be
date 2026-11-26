package com.aquasuite.ims.dto.finance.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DebtCallResultRequest(
        @NotNull CallResult callResult,
        @FutureOrPresent(message = "Ngày hẹn thanh toán không được ở trong quá khứ")
        LocalDate promisedPaymentDate,
        @Size(max = 500) String note) {
    public enum CallResult { PROMISED_TO_PAY, NEEDS_REVIEW, NO_ANSWER, NUMBER_UNREACHABLE }
}
