package com.aquasuite.ims.dto.finance.response;

import com.aquasuite.ims.enums.finance.DebtStatus;
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
