package com.aquasuite.ims.service.finance.impl;

import com.aquasuite.ims.service.finance.PaymentSettlementService;
import com.aquasuite.ims.dto.finance.command.PendingCustomerRefundCommand;
import com.aquasuite.ims.dto.finance.response.VerifiedPaymentSummary;
import com.aquasuite.ims.dto.finance.response.RefundSettlementView;
import com.aquasuite.ims.entity.finance.Transaction;
import com.aquasuite.ims.repository.finance.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentSettlementServiceImpl implements PaymentSettlementService {
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

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundSettlementView> findCustomerRefund(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .filter(transaction -> "EXPENSE".equalsIgnoreCase(transaction.getTransactionType())
                        && "CUSTOMER_REFUND".equalsIgnoreCase(transaction.getCategory()))
                .map(transaction -> new RefundSettlementView(transaction.getTransactionId(), transaction.getOrderId(),
                        transaction.getAmount(), transaction.getStatus()));
    }
}
