package com.aquasuite.ims.dto.finance.response;

import com.aquasuite.ims.enums.finance.DebtStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Explains the derived blacklist state without persisting a second customer status. */
public record BlacklistStatusResponse(
        UUID customerId,
        String customerStatus,
        DebtStatus debtStatus,
        boolean blacklisted,
        BigDecimal overdueBalance,
        String reasonCode) {}
