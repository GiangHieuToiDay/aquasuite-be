package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Integration test UC-70 trên PostgreSQL thật: xác minh/từ chối, vượt nghĩa vụ và đồng thời. */
@SpringBootTest
class PaymentVerificationPostgresIT extends PostgreSqlIntegrationTestSupport {
    @Autowired OrderPaymentService payments;
    @Autowired PaymentVerificationService verifications;
    @Autowired JdbcTemplate jdbc;
    @MockBean IamApi iam;

    private UUID seedOrder(String prefix, String total) {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), order = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, prefix + "-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', '0900000000')",
                customer, prefix + "-" + customer);
        jdbc.update("INSERT INTO orders(order_id, order_code, customer_id, order_channel, order_status, payment_status, order_date, total_amount) "
                + "VALUES (?, ?, ?, 'COUNTER', 'PROCESSING', 'UNPAID', now(), " + total + ")", order, prefix + "-" + order, customer);
        when(iam.getCurrentUserId()).thenReturn(actor);
        return order;
    }

    private UUID recordTransfer(UUID order, String amount) {
        return payments.record(order, new RecordOrderPaymentRequest(UUID.randomUUID(), new BigDecimal(amount),
                RecordOrderPaymentRequest.Method.BANK_TRANSFER, "REF", null)).transactionId();
    }

    @Test
    void verifyPendingTransferCountsAsPaidAndUpdatesOrder() {
        UUID order = seedOrder("VER", "100");
        payments.record(order, new RecordOrderPaymentRequest(UUID.randomUUID(), new BigDecimal("30"),
                RecordOrderPaymentRequest.Method.CASH, null, null));
        UUID transfer = recordTransfer(order, "70");

        var result = verifications.verify(transfer);

        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.approvedBy()).isNotNull();
        assertThat(payments.summary(order).verifiedPaid()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?", String.class, order)).isEqualTo("PAID");
    }

    @Test
    void verifyExceedingRemainingKeepsPendingAndKeepsInfo() {
        UUID order = seedOrder("OVR", "100");
        UUID a = recordTransfer(order, "70");
        UUID b = recordTransfer(order, "60");
        verifications.verify(a);

        assertThatThrownBy(() -> verifications.verify(b))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode().getCode()).isEqualTo("VER_003"));

        assertThat(jdbc.queryForObject("SELECT status FROM transactions WHERE transaction_id=?", String.class, b)).isEqualTo("PENDING");
        assertThat(payments.summary(order).verifiedPaid()).isEqualByComparingTo("70");
    }

    @Test
    void rejectStoresReasonAndDoesNotCountAsPaid() {
        UUID order = seedOrder("REJ", "100");
        UUID transfer = recordTransfer(order, "70");

        var result = verifications.reject(transfer, "Không thấy tiền về tài khoản");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT reject_reason FROM transactions WHERE transaction_id=?", String.class, transfer))
                .isEqualTo("Không thấy tiền về tài khoản");
        assertThat(payments.summary(order).verifiedPaid()).isEqualByComparingTo("0");
    }

    @Test
    void listFiltersByStatusAndExposesRejectReason() {
        UUID order = seedOrder("LST", "100");
        UUID a = recordTransfer(order, "70");
        UUID b = recordTransfer(order, "30");
        verifications.verify(a);
        verifications.reject(b, "Sai số tiền trên sao kê");

        // Đang chờ: không còn khoản nào.
        assertThat(verifications.list(order, "PENDING", 0, 20).totalElements()).isEqualTo(0);
        // Đã xác minh: đúng khoản a.
        var verified = verifications.list(order, "VERIFIED", 0, 20);
        assertThat(verified.content()).hasSize(1);
        assertThat(verified.content().get(0).transactionId()).isEqualTo(a);
        assertThat(verified.content().get(0).status()).isEqualTo("VERIFIED");
        // Đã từ chối: đúng khoản b và có lý do.
        var rejected = verifications.list(order, "REJECTED", 0, 20);
        assertThat(rejected.content()).hasSize(1);
        assertThat(rejected.content().get(0).transactionId()).isEqualTo(b);
        assertThat(rejected.content().get(0).rejectReason()).isEqualTo("Sai số tiền trên sao kê");
    }

    @Test
    void concurrentVerifyOfSameTransferAppliesOnce() throws Exception {
        UUID order = seedOrder("CON", "100");
        UUID transfer = recordTransfer(order, "70");
        CyclicBarrier gate = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt = () -> {
                gate.await(10, TimeUnit.SECONDS);
                try {
                    verifications.verify(transfer);
                    return "OK";
                } catch (AppException e) {
                    return e.getErrorCode().getCode();
                }
            };
            Future<String> x = pool.submit(attempt), y = pool.submit(attempt);
            List<String> outcomes = List.of(x.get(20, TimeUnit.SECONDS), y.get(20, TimeUnit.SECONDS));
            // Một lần thành công; lần còn lại thấy đã xử lý (VER_001), không cộng tiền hai lần.
            assertThat(outcomes).contains("OK");
            assertThat(outcomes).containsAnyOf("VER_001");
            assertThat(payments.summary(order).verifiedPaid()).isEqualByComparingTo("70");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=? AND status='VERIFIED'",
                    Integer.class, order)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
