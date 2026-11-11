package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.service.impl.PaymentVerificationServiceImpl;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentVerificationServiceTest {
    @Mock TransactionRepository transactions;
    @Mock SalesOrderApi sales;
    @Mock IamApi iam;
    @Mock com.aquasuite.ims.module.finance.internal.repository.CodRepository cod;
    @InjectMocks PaymentVerificationServiceImpl service;

    UUID orderId;
    UUID customerId;
    UUID actorId;
    UUID paymentId;
    SalesOrderPaymentInfoDto order;

    @BeforeEach
    void setup() {
        lenient().when(cod.held(any())).thenReturn(BigDecimal.ZERO);
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        order = SalesOrderPaymentInfoDto.builder().orderId(orderId).customerId(customerId)
                .totalAmount(new BigDecimal("10000000")).validForPayment(true).build();
    }

    private Transaction transfer(String status, String amount) {
        return Transaction.builder().transactionId(paymentId).transactionCode("PAY-" + paymentId)
                .transactionType("RECEIPT").paymentMethod("BANK_TRANSFER").status(status)
                .customerId(customerId).orderId(orderId).amount(new BigDecimal(amount)).createdBy(UUID.randomUUID()).build();
    }

    @Test void verifySuccessWithinObligationMarksVerifiedAndUpdatesOrder() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        Transaction pending = transfer("PENDING", "7000000");
        when(transactions.findOrderIdById(paymentId)).thenReturn(Optional.of(orderId));
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByIdForUpdate(paymentId)).thenReturn(Optional.of(pending));
        var cash = Transaction.builder().customerId(customerId).status("VERIFIED").amount(new BigDecimal("3000000")).build();
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(cash, pending));

        var result = service.verify(paymentId);

        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.approvedBy()).isEqualTo(actorId);
        verify(transactions).saveAndFlush(argThat(t -> "VERIFIED".equals(t.getStatus())));
        verify(sales).updatePaymentStatus(orderId, "PAID");
    }

    @Test void verifyExceedingRemainingKeepsPendingAndDoesNotUpdateOrder() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        Transaction pending = transfer("PENDING", "6000000");
        when(transactions.findOrderIdById(paymentId)).thenReturn(Optional.of(orderId));
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByIdForUpdate(paymentId)).thenReturn(Optional.of(pending));
        // Đã xác minh 7tr -> còn 3tr; khoản 6tr vượt nghĩa vụ.
        var verified = Transaction.builder().customerId(customerId).status("VERIFIED").amount(new BigDecimal("7000000")).build();
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(verified, pending));

        assertThatThrownBy(() -> service.verify(paymentId)).isInstanceOf(AppException.class);
        verify(transactions, never()).saveAndFlush(any());
        verify(sales, never()).updatePaymentStatus(any(), any());
        assertThat(pending.getStatus()).isEqualTo("PENDING");
    }

    @Test void verifyAlreadyProcessedIsRejectedWith409() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        Transaction done = transfer("VERIFIED", "7000000");
        when(transactions.findOrderIdById(paymentId)).thenReturn(Optional.of(orderId));
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByIdForUpdate(paymentId)).thenReturn(Optional.of(done));
        assertThatThrownBy(() -> service.verify(paymentId)).isInstanceOf(AppException.class);
        verify(transactions, never()).saveAndFlush(any());
        verify(sales, never()).updatePaymentStatus(any(), any());
    }

    @Test void verifyNonBankTransferIsRejected() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        Transaction cash = Transaction.builder().transactionId(paymentId).transactionType("RECEIPT")
                .paymentMethod("CASH").status("PENDING").orderId(orderId).customerId(customerId)
                .amount(new BigDecimal("1000000")).build();
        when(transactions.findOrderIdById(paymentId)).thenReturn(Optional.of(orderId));
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByIdForUpdate(paymentId)).thenReturn(Optional.of(cash));
        assertThatThrownBy(() -> service.verify(paymentId)).isInstanceOf(AppException.class);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void rejectSetsRejectedWithReasonAndActor() {
        when(iam.getCurrentUserId()).thenReturn(actorId);
        Transaction pending = transfer("PENDING", "7000000");
        when(transactions.findByIdForUpdate(paymentId)).thenReturn(Optional.of(pending));

        var result = service.reject(paymentId, "Không thấy tiền về tài khoản");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.rejectReason()).isEqualTo("Không thấy tiền về tài khoản");
        assertThat(result.approvedBy()).isEqualTo(actorId);
        verify(transactions).saveAndFlush(argThat(t -> "REJECTED".equals(t.getStatus())));
    }

    @Test void rejectWithoutReasonIsRejected() {
        assertThatThrownBy(() -> service.reject(paymentId, "  ")).isInstanceOf(AppException.class);
        verify(transactions, never()).findByIdForUpdate(any());
    }
}
