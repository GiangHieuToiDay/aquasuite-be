package com.aquasuite.ims.dto.finance.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentAllocationTargetResponse(
        UUID invoiceId,
        String invoiceCode,
        String invoiceSymbol,
        OffsetDateTime issueDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal verifiedPaidAmount,
        BigDecimal outstandingAmount) {}
