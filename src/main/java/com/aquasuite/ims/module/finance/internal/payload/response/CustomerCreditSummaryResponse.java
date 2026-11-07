package com.aquasuite.ims.module.finance.internal.payload.response;

import com.aquasuite.ims.module.finance.internal.payload.DebtStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Minimal credit projection for the sales-order flow. It deliberately excludes
 * invoices, receipts, reminder history, contact details and audit information.
 */
@Builder
public record CustomerCreditSummaryResponse(
        UUID customerId,
        String customerCode,
        String customerName,
        String customerStatus,
        Integer creditTermDays,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance,
        BigDecimal overdueBalance,
        BigDecimal availableCredit,
        DebtStatus debtStatus) {}
