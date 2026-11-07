package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aquasuite.ims.module.finance.internal.repository.FinanceLockRepository;
import com.aquasuite.ims.module.finance.internal.service.impl.OrderPaymentServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {
    @Mock TransactionRepository transactions;
    @Mock SalesOrderApi sales;
    @Mock IamApi iam;
    @Mock FinanceLockRepository locks;
    @InjectMocks OrderPaymentServiceImpl service;
    UUID orderId;
    UUID customerId;
    UUID actorId;
    SalesOrderPaymentInfoDto order;

    @BeforeEach void setup() {
        orderId = UUID.randomUUID(); customerId = UUID.randomUUID(); actorId = UUID.randomUUID();
        order = SalesOrderPaymentInfoDto.builder().orderId(orderId).customerId(customerId)
                .totalAmount(new BigDecimal("10000000")).validForPayment(true).build();
    }

    @Test void cashIsVerifiedAndUpdatesOrderWithinTheCall() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT")).thenReturn(List.of());
        var result = service.record(orderId, new RecordOrderPaymentRequest(UUID.randomUUID(),
                new BigDecimal("3000000"), RecordOrderPaymentRequest.Method.CASH, null, null));
        assertThat(result.status()).isEqualTo("VERIFIED");
        verify(transactions).saveAndFlush(argThat(t -> customerId.equals(t.getCustomerId())
                && "RECEIPT".equals(t.getTransactionType()) && actorId.equals(t.getApprovedBy())));
        verify(sales).updatePaymentStatus(orderId, "PARTIALLY_PAID");
    }

    @Test void pendingTransferDoesNotReduceBalanceOrUpdateOrder() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT")).thenReturn(List.of());
        var result = service.record(orderId, new RecordOrderPaymentRequest(UUID.randomUUID(),
                new BigDecimal("7000000"), RecordOrderPaymentRequest.Method.BANK_TRANSFER, "REF", null));
        assertThat(result.status()).isEqualTo("PENDING");
        verify(sales, never()).updatePaymentStatus(any(), any());
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        var paid = Transaction.builder().customerId(customerId).status("VERIFIED").amount(new BigDecimal("3000000")).build();
        var pending = Transaction.builder().customerId(customerId).status("PENDING").amount(new BigDecimal("7000000")).build();
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT")).thenReturn(List.of(paid, pending));
        var summary = service.summary(orderId);
        assertThat(summary.remaining()).isEqualByComparingTo("7000000");
        assertThat(summary.pendingAmount()).isEqualByComparingTo("7000000");
        assertThat(summary.paymentStatus()).isEqualTo("PARTIALLY_PAID");
    }

    @Test void sameRequestKeyReturnsExistingReceiptWithoutWritingAgain() {
        UUID key = UUID.randomUUID();
        when(iam.getCurrentUserId()).thenReturn(actorId);
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        var old = Transaction.builder().transactionId(UUID.randomUUID()).orderId(orderId).createdBy(actorId)
                .amount(BigDecimal.TEN).paymentMethod("CASH").status("VERIFIED").build();
        when(transactions.findByIdempotencyKey(key)).thenReturn(Optional.of(old));
        assertThat(service.record(orderId, new RecordOrderPaymentRequest(key, BigDecimal.TEN,
                RecordOrderPaymentRequest.Method.CASH, null, null)).transactionId()).isEqualTo(old.getTransactionId());
        verify(transactions, never()).saveAndFlush(any());
        verify(sales, never()).updatePaymentStatus(any(), any());
    }

    @Test void excessReceiptIsRejectedWithoutWriting() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        var paid = Transaction.builder().customerId(customerId).status("VERIFIED").amount(new BigDecimal("9000000")).build();
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT")).thenReturn(List.of(paid));
        var request = new RecordOrderPaymentRequest(UUID.randomUUID(), new BigDecimal("2000000"),
                RecordOrderPaymentRequest.Method.CASH, null, null);
        assertThatThrownBy(() -> service.record(orderId, request)).isInstanceOf(AppException.class);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void inconsistentCustomerOnExistingReceiptDoesNotProduceAFalseBalance() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(Transaction.builder().customerId(UUID.randomUUID())
                        .status("VERIFIED").amount(BigDecimal.TEN).build()));
        assertThatThrownBy(() -> service.summary(orderId)).isInstanceOf(AppException.class);
    }
}
