package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.api.FinanceApi;
import com.aquasuite.ims.module.finance.api.dto.CreatePaymentTransactionCommand;
import com.aquasuite.ims.module.finance.api.dto.PaymentTransactionResponseDto;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceApiImpl implements FinanceApi {

    private final TransactionRepository transactionRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    @Transactional
    public PaymentTransactionResponseDto recordPaymentTransaction(CreatePaymentTransactionCommand command) {
        if (command == null || command.amount() == null) {
            log.warn("Ignored invalid transaction command (null or missing amount)");
            return null;
        }

        String transactionCode = generateTransactionCode();
        OffsetDateTime now = OffsetDateTime.now();

        Transaction transaction = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .transactionCode(transactionCode)
                .transactionType("RECEIPT")
                .paymentType(command.paymentType() != null ? command.paymentType() : "FULL_PAYMENT")
                .category(command.category() != null ? command.category() : "SALES_RETAIL")
                .orderId(command.orderId())
                .invoiceId(command.invoiceId())
                .customerId(command.customerId())
                .amount(command.amount())
                .paymentMethod(command.paymentMethod() != null ? command.paymentMethod() : "CASH")
                .referenceNo(command.referenceNo())
                .status(command.status() != null ? command.status() : "VERIFIED")
                .description(command.description())
                .createdBy(command.createdBy())
                .transactionDate(now)
                .createdAt(now)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        log.info("Recorded payment transaction {} for order {} (invoice {}): amount={}, method={}",
                transactionCode, command.orderId(), command.invoiceId(), command.amount(), command.paymentMethod());

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponseDto> getTransactionsByOrderId(UUID orderId) {
        if (orderId == null) return Collections.emptyList();
        return transactionRepository.findByOrderId(orderId).stream()
                .map(this::toDto)
                .toList();
    }

    private String generateTransactionCode() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String prefix = "REC-" + datePart + "-";

        long currentCount = transactionRepository.countByTransactionCodeStartingWith(prefix);
        long nextSequence = currentCount + 1;

        String code = String.format("%s%04d", prefix, nextSequence);
        while (transactionRepository.existsByTransactionCode(code)) {
            nextSequence++;
            code = String.format("%s%04d", prefix, nextSequence);
        }

        return code;
    }

    private PaymentTransactionResponseDto toDto(Transaction tx) {
        return PaymentTransactionResponseDto.builder()
                .transactionId(tx.getTransactionId())
                .transactionCode(tx.getTransactionCode())
                .transactionType(tx.getTransactionType())
                .paymentType(tx.getPaymentType())
                .category(tx.getCategory())
                .customerId(tx.getCustomerId())
                .orderId(tx.getOrderId())
                .invoiceId(tx.getInvoiceId())
                .amount(tx.getAmount())
                .paymentMethod(tx.getPaymentMethod())
                .referenceNo(tx.getReferenceNo())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .transactionDate(tx.getTransactionDate())
                .createdBy(tx.getCreatedBy())
                .build();
    }
}
