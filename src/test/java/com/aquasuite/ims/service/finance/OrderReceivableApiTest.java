package com.aquasuite.ims.service.finance;

import com.aquasuite.ims.entity.finance.Transaction;
import com.aquasuite.ims.repository.finance.TransactionRepository;
import com.aquasuite.ims.service.finance.impl.OrderReceivableServiceImpl;
import com.aquasuite.ims.service.sales.SalesOrderService;
import com.aquasuite.ims.dto.sales.response.SalesOrderMilestoneDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderPaymentInfoDto;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReceivableApiTest {
    @Mock TransactionRepository transactions;
    @Mock SalesOrderService sales;
    @InjectMocks OrderReceivableServiceImpl api;

    UUID orderId;
    UUID customerId;

    @BeforeEach
    void setup() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    private SalesOrderPaymentInfoDto order(String total, String deposit) {
        return SalesOrderPaymentInfoDto.builder().orderId(orderId).customerId(customerId)
                .totalAmount(new BigDecimal(total)).depositAmount(deposit == null ? null : new BigDecimal(deposit))
                .paymentStatus("PARTIALLY_PAID").validForPayment(true).build();
    }

    private Transaction receipt(String status, String amount) {
        return Transaction.builder().customerId(customerId).status(status).amount(new BigDecimal(amount)).build();
    }

    @Test void depositSatisfiedWhenVerifiedCoversDepositAmount() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order("100000000", "30000000")));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(receipt("VERIFIED", "30000000"), receipt("PENDING", "20000000")));

        var view = api.getOrderReceivable(orderId).orElseThrow();

        assertThat(view.verifiedPaid()).isEqualByComparingTo("30000000");
        assertThat(view.pendingAmount()).isEqualByComparingTo("20000000");
        assertThat(view.remaining()).isEqualByComparingTo("70000000");
        assertThat(view.depositRequired()).isEqualByComparingTo("30000000");
        assertThat(view.depositSatisfied()).isTrue();
    }

    @Test void pendingDoesNotSatisfyDeposit() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order("100000000", "30000000")));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(receipt("PENDING", "30000000")));

        var view = api.getOrderReceivable(orderId).orElseThrow();

        assertThat(view.verifiedPaid()).isEqualByComparingTo("0");
        assertThat(view.depositSatisfied()).isFalse();
    }

    @Test void depositFallsBackToFirstMilestoneWhenNoDepositAmount() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order("100000000", null)));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(receipt("VERIFIED", "40000000")));
        when(sales.getOrderPaymentMilestones(orderId)).thenReturn(List.of(
                SalesOrderMilestoneDto.builder().milestoneId(UUID.randomUUID()).orderId(orderId)
                        .stageName("Đặt cọc").amount(new BigDecimal("30000000")).sortOrder(1).build()));

        var view = api.getOrderReceivable(orderId).orElseThrow();

        assertThat(view.depositRequired()).isEqualByComparingTo("30000000");
        assertThat(view.depositSatisfied()).isTrue();
    }

    @Test void missingOrderReturnsEmpty() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.empty());
        assertThat(api.getOrderReceivable(orderId)).isEmpty();
    }

    @Test void noDepositNoMilestoneLeavesDepositRequiredZero() {
        when(sales.getOrderPaymentInfo(orderId)).thenReturn(Optional.of(order("100000000", null)));
        when(transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(receipt("VERIFIED", "10000000")));
        lenient().when(sales.getOrderPaymentMilestones(orderId)).thenReturn(List.of());

        var view = api.getOrderReceivable(orderId).orElseThrow();

        assertThat(view.depositRequired()).isEqualByComparingTo("0");
        assertThat(view.depositSatisfied()).isFalse();
    }
}
