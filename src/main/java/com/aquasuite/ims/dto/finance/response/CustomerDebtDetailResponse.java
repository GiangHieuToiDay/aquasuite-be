package com.aquasuite.ims.dto.finance.response;

import com.aquasuite.ims.dto.audit.response.AuditLogResponse;
import com.aquasuite.ims.dto.crm.response.CustomerDebtProfileDto;

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
