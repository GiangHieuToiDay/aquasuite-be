package com.aquasuite.ims.service.finance;

import com.aquasuite.ims.entity.finance.Transaction;
import com.aquasuite.ims.dto.finance.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.dto.finance.response.OrderMilestoneProgressResponse;
import com.aquasuite.ims.repository.finance.TransactionRepository;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.SalesOrderMilestoneDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aquasuite.ims.repository.finance.FinanceLockRepository;
import com.aquasuite.ims.service.finance.impl.OrderPaymentServiceImpl;
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
    @Mock SalesQueryService sales;
    @Mock IamQueryService iam;
    @Mock FinanceLockRepository locks;
    @Mock PaymentAllocationService paymentAllocationService;
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
        verify(paymentAllocationService).autoAllocateVerifiedReceipt(result.transactionId(), actorId);
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

    private SalesOrderMilestoneDto milestone(String name, int sort, String amount) {
        return SalesOrderMilestoneDto.builder().milestoneId(UUID.randomUUID()).orderId(orderId)
                .stageName(name).percent(null).amount(new BigDecimal(amount)).sortOrder(sort).build();
    }

    @Test void milestoneProgressAllocatesVerifiedSequentiallyAndSpillsOver() {
        // Đơn 100 triệu; mốc 30/40/30; đã thu VERIFIED 40 triệu (một khoản 20 PENDING không tính).
        var order100 = SalesOrderPaymentInfoDto.builder().orderId(orderId).customerId(customerId)
                .totalAmount(new BigDecimal("100000000")).validForPayment(true).build();
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order100));
        var verified = Transaction.builder().customerId(customerId).status("VERIFIED").amount(new BigDecimal("40000000")).build();
        var pending = Transaction.builder().customerId(customerId).status("PENDING").amount(new BigDecimal("20000000")).build();
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(verified, pending));
        when(sales.getOrderPaymentMilestones(orderId)).thenReturn(List.of(
                milestone("Cọc", 1, "30000000"), milestone("Đợt 1", 2, "40000000"), milestone("Đợt 2", 3, "30000000")));

        OrderMilestoneProgressResponse result = service.milestoneProgress(orderId);

        assertThat(result.hasMilestones()).isTrue();
        assertThat(result.verifiedPaid()).isEqualByComparingTo("40000000");
        assertThat(result.pendingAmount()).isEqualByComparingTo("20000000");
        assertThat(result.remaining()).isEqualByComparingTo("60000000");
        assertThat(result.milestones()).hasSize(3);
        assertThat(result.milestones().get(0).paidAmount()).isEqualByComparingTo("30000000");
        assertThat(result.milestones().get(0).remainingAmount()).isEqualByComparingTo("0");
        assertThat(result.milestones().get(1).paidAmount()).isEqualByComparingTo("10000000");
        assertThat(result.milestones().get(1).remainingAmount()).isEqualByComparingTo("30000000");
        assertThat(result.milestones().get(2).paidAmount()).isEqualByComparingTo("0");
        assertThat(result.milestones().get(2).remainingAmount()).isEqualByComparingTo("30000000");
    }

    @Test void milestoneProgressWithoutMilestonesStillReturnsOrderTotals() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT")).thenReturn(List.of());
        when(sales.getOrderPaymentMilestones(orderId)).thenReturn(List.of());

        OrderMilestoneProgressResponse result = service.milestoneProgress(orderId);

        assertThat(result.hasMilestones()).isFalse();
        assertThat(result.milestones()).isEmpty();
        assertThat(result.remaining()).isEqualByComparingTo("10000000");
    }

    @Test void inconsistentCustomerOnExistingReceiptDoesNotProduceAFalseBalance() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(Transaction.builder().customerId(UUID.randomUUID())
                        .status("VERIFIED").amount(BigDecimal.TEN).build()));
        assertThatThrownBy(() -> service.summary(orderId)).isInstanceOf(AppException.class);
    }

    @Test void ensureOnlinePayosReceiptRecorded_CreatesVerifiedReceiptAndLinksStoreInvoice() {
        UUID payosPaymentId = UUID.randomUUID();
        UUID storeInvoiceId = UUID.randomUUID();
        var onlineOrder = SalesOrderPaymentInfoDto.builder()
                .orderId(orderId)
                .customerId(customerId)
                .storeInvoiceId(storeInvoiceId)
                .orderChannel("ONLINE")
                .paymentStatus("PAID")
                .totalAmount(new BigDecimal("4000"))
                .validForPayment(true)
                .build();
        when(sales.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(onlineOrder));
        when(transactions.findByIdempotencyKey(payosPaymentId)).thenReturn(Optional.empty());
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT")).thenReturn(List.of());

        var recorded = service.ensureOnlinePayosReceiptRecorded(
                orderId, payosPaymentId, 99887766L, new BigDecimal("4000"), null, actorId);

        assertThat(recorded).isNotNull();
        assertThat(recorded.status()).isEqualTo("VERIFIED");
        assertThat(recorded.amount()).isEqualByComparingTo("4000");
        assertThat(recorded.invoiceId()).isEqualTo(storeInvoiceId);
        verify(transactions).saveAndFlush(argThat(t ->
                "PAYOS-99887766".equals(t.getTransactionCode())
                        && "VERIFIED".equals(t.getStatus())
                        && "BANK_TRANSFER".equals(t.getPaymentMethod())
                        && storeInvoiceId.equals(t.getInvoiceId())));
    }
}
