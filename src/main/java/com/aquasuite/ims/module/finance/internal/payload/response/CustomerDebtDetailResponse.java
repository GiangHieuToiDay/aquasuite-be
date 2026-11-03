package com.aquasuite.ims.module.finance.internal.payload.response;

import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;

import java.math.BigDecimal;
import java.util.List;

public record CustomerDebtDetailResponse(
        CustomerDebtProfileDto customer,
        CustomerDebtSummaryResponse summary,
        List<InvoiceDebtResponse> invoices,
        List<OrderDebtResponse> orders,
        List<PaymentReceiptResponse> verifiedReceipts,
        List<UnallocatedReceiptResponse> unallocatedOrderReceipts,
        BigDecimal unallocatedVerifiedReceiptAmount,
        List<AuditLogResponse> reminderHistory) {}
