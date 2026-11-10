package com.aquasuite.ims.module.finance.api;

import com.aquasuite.ims.module.finance.api.dto.PendingCustomerRefundCommand;
import com.aquasuite.ims.module.finance.api.dto.VerifiedPaymentSummary;

import java.util.UUID;

/** Public payment facts/commands consumed by Sales modification workflows. */
public interface PaymentSettlementApi {
    VerifiedPaymentSummary getVerifiedPaymentSummary(UUID orderId);

    UUID createPendingCustomerRefund(PendingCustomerRefundCommand command);
}
