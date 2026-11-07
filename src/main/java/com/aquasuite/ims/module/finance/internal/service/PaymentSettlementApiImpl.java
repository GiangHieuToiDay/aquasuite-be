package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.PaymentSettlementApi;
import com.aquasuite.ims.module.finance.api.dto.PendingCustomerRefundCommand;
import com.aquasuite.ims.module.finance.api.dto.VerifiedPaymentSummary;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentSettlementApiImpl implements PaymentSettlementApi {
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public VerifiedPaymentSummary getVerifiedPaymentSummary(UUID orderId) {
        BigDecimal amount = transactionRepository.sumVerifiedAmountByOrderId(orderId);
        return new VerifiedPaymentSummary(orderId, amount == null ? BigDecimal.ZERO : amount);
    }

    @Override
    @Transactional
    public UUID createPendingCustomerRefund(PendingCustomerRefundCommand command) {
        Transaction refund = Transaction.builder()
                .transactionCode("REFUND-" + UUID.randomUUID())
                .transactionType("EXPENSE")
                .paymentType("REFUND")
                .category("CUSTOMER_REFUND")
                .customerId(command.customerId())
                .orderId(command.orderId())
                .invoiceId(command.invoiceId())
                .amount(command.amount())
                .paymentMethod("ORIGINAL_PAYMENT")
                .status("PENDING")
                .description(command.reason())
                .createdBy(command.requestedBy())
                .build();
        return transactionRepository.save(refund).getTransactionId();
    }
}
