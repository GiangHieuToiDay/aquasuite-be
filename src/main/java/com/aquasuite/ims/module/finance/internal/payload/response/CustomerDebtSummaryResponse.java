package com.aquasuite.ims.module.finance.internal.payload.response;

import com.aquasuite.ims.module.finance.internal.payload.DebtStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CustomerDebtSummaryResponse(
        UUID customerId,
        String customerCode,
        String customerName,
        String phone,
        String companyName,
        String customerType,
        BigDecimal creditLimit,
        BigDecimal unbilledCommitment,
        BigDecimal issuedInvoiceReceivable,
        BigDecimal unallocatedAdvanceAmount,
        BigDecimal outstandingBalance,
        BigDecimal overdueBalance,
        BigDecimal availableCredit,
        LocalDate nearestDueDate,
        DebtStatus debtStatus) {}
