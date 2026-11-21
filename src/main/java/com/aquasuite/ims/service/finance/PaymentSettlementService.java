package com.aquasuite.ims.service.finance;

import com.aquasuite.ims.dto.finance.command.PendingCustomerRefundCommand;
import com.aquasuite.ims.dto.finance.response.VerifiedPaymentSummary;
import com.aquasuite.ims.dto.finance.response.RefundSettlementView;

import java.util.Optional;
import java.util.UUID;

/** Public payment facts/commands consumed by Sales modification workflows. */
public interface PaymentSettlementService {
    VerifiedPaymentSummary getVerifiedPaymentSummary(UUID orderId);

    UUID createPendingCustomerRefund(PendingCustomerRefundCommand command);

    Optional<RefundSettlementView> findCustomerRefund(UUID transactionId);
}
