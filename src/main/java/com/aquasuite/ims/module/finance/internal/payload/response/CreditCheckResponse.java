package com.aquasuite.ims.module.finance.internal.payload.response;

import com.aquasuite.ims.module.finance.internal.payload.DebtStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CreditCheckResponse(
        UUID customerId,
        boolean approved,
        boolean overrideAllowed,
        DebtStatus debtStatus,
        String reasonCode,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance,
        BigDecimal overdueBalance,
        BigDecimal availableCredit) {}
