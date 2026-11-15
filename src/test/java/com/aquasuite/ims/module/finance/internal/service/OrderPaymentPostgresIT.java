package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.finance.internal.controller.OrderPaymentController;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderPaymentPostgresIT extends PostgreSqlIntegrationTestSupport {
    @Autowired OrderPaymentService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired SalesOrderService salesOrderService;
    @Autowired OrderPaymentProofService proofService;
    @Autowired OrderPaymentController paymentController;
    @Autowired TransactionTemplate transactionTemplate;
    @MockBean IamApi iam;
    @MockBean CatalogApi catalog;
    @MockBean InventoryApi inventory;
    @MockBean com.aquasuite.ims.module.inventory.internal.service.InventoryService inventoryService;
    @SpyBean com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository invoiceItems;

    @Test
    void sameIdempotencyKeyOnDifferentOrdersReturnsBusinessConflict() throws Exception {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), first = UUID.randomUUID();
        UUID second = UUID.randomUUID(), key = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "key-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', ?)",
                customer, "K-" + customer, "09" + System.nanoTime());
        for (UUID order : List.of(first, second)) {
            jdbc.update("INSERT INTO orders(order_id, order_code, customer_id, order_channel, order_status, payment_status, order_date, total_amount) "
                    + "VALUES (?, ?, ?, 'COUNTER', 'PROCESSING', 'UNPAID', now(), 100)", order, "K-" + order, customer);
        }
        when(iam.getCurrentUserId()).thenReturn(actor);
        CyclicBarrier gate = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.function.Function<UUID, Callable<String>> attempt = order -> () -> {
                gate.await(10, TimeUnit.SECONDS);
                try {
                    service.record(order, new RecordOrderPaymentRequest(key, BigDecimal.TEN,
                            RecordOrderPaymentRequest.Method.CASH, null, null));
                    return "OK";
                } catch (AppException e) {
                    return e.getErrorCode().getCode();
                }
            };
            var a = pool.submit(attempt.apply(first));
            var b = pool.submit(attempt.apply(second));
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "PAY_004");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE idempotency_key=?", Integer.class, key))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void salesStatusFailureRollsBackPayment() {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), order = UUID.randomUUID(), key = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "payrollback-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', ?)",
                customer, "PR-" + customer, "09" + System.nanoTime());
        jdbc.update("INSERT INTO orders(order_id, order_code, customer_id, order_channel, order_status, payment_status, order_date, total_amount) "
                + "VALUES (?, ?, ?, 'COUNTER', 'PROCESSING', 'UNPAID', now(), 100)", order, "PR-" + order, customer);
        when(iam.getCurrentUserId()).thenReturn(actor);
        // Constraint chỉ tồn tại trong database Testcontainers, mô phỏng lỗi ghi trạng thái khi commit.
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT test_payment_status_failure CHECK (order_id <> '"
                + order + "'::uuid OR payment_status = 'UNPAID')");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(order,
                    new RecordOrderPaymentRequest(key, BigDecimal.TEN, RecordOrderPaymentRequest.Method.CASH, null, null)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE idempotency_key=?", Integer.class, key)).isZero();
            assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?", String.class, order)).isEqualTo("UNPAID");
        } finally {
            jdbc.execute("ALTER TABLE orders DROP CONSTRAINT test_payment_status_failure");
        }
    }

    @Test
    void paymentUsesChangedOrderTotalAfterConcurrentWriterReleasesLock() throws Exception {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), order = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "total-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', ?)",
                customer, "T-" + customer, "09" + System.nanoTime());
        jdbc.update("INSERT INTO orders(order_id, order_code, customer_id, order_channel, order_status, payment_status, order_date, total_amount) "
                + "VALUES (?, ?, ?, 'COUNTER', 'PROCESSING', 'UNPAID', now(), 100)", order, "T-" + order, customer);
        when(iam.getCurrentUserId()).thenReturn(actor);
        CountDownLatch updated = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.update("UPDATE orders SET total_amount=50 WHERE order_id=?", order);
                updated.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting to commit");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> payment = pool.submit(() -> {
                try {
                    service.record(order, new RecordOrderPaymentRequest(UUID.randomUUID(), new BigDecimal("60"),
                            RecordOrderPaymentRequest.Method.CASH, null, null));
                    return "OK";
                } catch (AppException e) {
                    return e.getErrorCode().getCode();
                }
            });
            release.countDown();
            writer.get(20, TimeUnit.SECONDS);
            assertThat(payment.get(20, TimeUnit.SECONDS)).isEqualTo("PAY_003");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?", Integer.class, order)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCashPaymentsCannotExceedOrderTotal() throws Exception {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), order = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "pay-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', ?)",
                customer, "P-" + customer, "09" + System.nanoTime());
        jdbc.update("INSERT INTO orders(order_id, order_code, customer_id, order_channel, order_status, payment_status, order_date, total_amount) "
                + "VALUES (?, ?, ?, 'COUNTER', 'PROCESSING', 'UNPAID', now(), 100)", order, "P-" + order, customer);
        when(iam.getCurrentUserId()).thenReturn(actor);
        CyclicBarrier gate = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                gate.await(10, TimeUnit.SECONDS);
                try {
                    service.record(order, new RecordOrderPaymentRequest(UUID.randomUUID(), new BigDecimal("60"),
                            RecordOrderPaymentRequest.Method.CASH, null, null));
                    return true;
                } catch (AppException ex) {
                    return false;
                }
            };
            Future<Boolean> a = pool.submit(attempt), b = pool.submit(attempt);
            assertThat((a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE order_id=?", Integer.class, order)).isEqualTo(1);
            assertThat(service.summary(order).verifiedPaid()).isEqualByComparingTo("60");
            assertThat(jdbc.queryForObject("SELECT payment_status FROM orders WHERE order_id=?", String.class, order))
                    .isEqualTo("PARTIALLY_PAID");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simultaneousCreditOrdersShareCustomerLimitAndCreateOneStoreInvoice() throws Exception {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID();
        UUID variant = jdbc.queryForObject("SELECT variant_id FROM product_variants LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "credit-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone, customer_type, credit_limit, credit_term_days) "
                + "VALUES (?, ?, 'Test', ?, 'CONTRACTOR', 100, 30)", customer, "C-" + customer, "09" + System.nanoTime());
        when(iam.getCurrentUserId()).thenReturn(actor);
        when(catalog.getSkuSummary(variant)).thenReturn(java.util.Optional.of(CatalogSkuSummaryDto.builder()
                .variantId(variant).sku("TEST").productName("Test").variantName("Test")
                .sellingPrice(new BigDecimal("60")).status("ACTIVE").build()));
        when(inventory.checkAvailability(variant, BigDecimal.ONE)).thenReturn(true);
        CyclicBarrier gate = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> create = () -> {
                gate.await(10, TimeUnit.SECONDS);
                try {
                    salesOrderService.createSalesOrder(CreateSalesOrderRequest.builder().customerId(customer)
                            .paymentMethod(PaymentMethod.CREDIT).items(List.of(CreateOrderItemRequest.builder()
                                    .variantId(variant).quantity(BigDecimal.ONE).build())).build());
                    return true;
                } catch (AppException ex) {
                    return false;
                }
            };
            var a = pool.submit(create); var b = pool.submit(create);
            assertThat((a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id=?", Integer.class, customer)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_invoices WHERE customer_id=? AND invoice_type='STORE' AND status='DRAFT'", Integer.class, customer)).isEqualTo(1);
            UUID orderId = jdbc.queryForObject("SELECT order_id FROM orders WHERE customer_id=?", UUID.class, customer);
            service.record(orderId, new RecordOrderPaymentRequest(UUID.randomUUID(), new BigDecimal("20"),
                    RecordOrderPaymentRequest.Method.CASH, null, null));
            assertThat(jdbc.queryForObject("SELECT invoice_id FROM transactions WHERE order_id=?", UUID.class, orderId)).isNotNull();
            var detail = customerDebtService.detail(customer);
            assertThat(detail.summary().outstandingBalance()).isEqualByComparingTo("40");
            assertThat(detail.unallocatedVerifiedReceiptAmount()).isEqualByComparingTo("20");
            assertThat(detail.invoices()).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Autowired CustomerDebtService customerDebtService;

    @Test
    void blockedCustomerCannotCreateCashOrCreditOrder() {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), variant = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "blocked-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone, status) VALUES (?, ?, 'Test', ?, 'BLOCKED')",
                customer, "B-" + customer, "09" + System.nanoTime());
        when(iam.getCurrentUserId()).thenReturn(actor);
        for (PaymentMethod method : List.of(PaymentMethod.CASH, PaymentMethod.CREDIT)) {
            var request = CreateSalesOrderRequest.builder().customerId(customer).paymentMethod(method)
                    .items(List.of(CreateOrderItemRequest.builder().variantId(variant).quantity(BigDecimal.ONE).build())).build();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                    .isInstanceOf(AppException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id=?", Integer.class, customer)).isZero();
    }

    @Test
    void invoiceItemFailureRollsBackOrderAndStoreInvoice() {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID();
        UUID variant = jdbc.queryForObject("SELECT variant_id FROM product_variants LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "rollback-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', ?)",
                customer, "R-" + customer, "09" + System.nanoTime());
        when(iam.getCurrentUserId()).thenReturn(actor);
        when(catalog.getSkuSummary(variant)).thenReturn(java.util.Optional.of(CatalogSkuSummaryDto.builder()
                .variantId(variant).sku("TEST").productName("Test").variantName("Test")
                .sellingPrice(BigDecimal.TEN).status("ACTIVE").build()));
        when(inventory.checkAvailability(variant, BigDecimal.ONE)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated invoice failure"))
                .when(invoiceItems).saveAll(org.mockito.ArgumentMatchers.anyList());
        var request = CreateSalesOrderRequest.builder().customerId(customer).paymentMethod(PaymentMethod.CASH)
                .items(List.of(CreateOrderItemRequest.builder().variantId(variant).quantity(BigDecimal.ONE).build())).build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> salesOrderService.createSalesOrder(request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE customer_id=?", Integer.class, customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_invoices WHERE customer_id=?", Integer.class, customer)).isZero();
    }

    @Test
    void transferProofIsStoredPrivatelyAndBoundToOrder() throws Exception {
        UUID actor = UUID.randomUUID(), customer = UUID.randomUUID(), order = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, email, password_hash, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                actor, "proof-" + actor, actor + "@example.invalid", "{disabled-test-account}");
        jdbc.update("INSERT INTO customers(customer_id, customer_code, customer_name, phone) VALUES (?, ?, 'Test', ?)",
                customer, "P-" + customer, "09" + System.nanoTime());
        jdbc.update("INSERT INTO orders(order_id, order_code, customer_id, order_channel, order_status, payment_status, order_date, total_amount) "
                + "VALUES (?, ?, ?, 'COUNTER', 'PROCESSING', 'UNPAID', now(), 100)", order, "P-" + order, customer);
        when(iam.getCurrentUserId()).thenReturn(actor);
        var payment = service.record(order, new RecordOrderPaymentRequest(UUID.randomUUID(), BigDecimal.TEN,
                RecordOrderPaymentRequest.Method.BANK_TRANSFER, "REF", null));
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        proofService.upload(order, payment.transactionId(), new MockMultipartFile("file", "proof.pdf", "application/pdf", pdf));
        assertThat(proofService.read(order, payment.transactionId()).content()).isEqualTo(pdf);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proofService.read(UUID.randomUUID(), payment.transactionId()))
                .isInstanceOf(AppException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proofService.upload(order, payment.transactionId(),
                new MockMultipartFile("file", "proof.pdf", "application/pdf", pdf))).isInstanceOf(AppException.class);
    }

    @Test
    void accountantCannotRecordPayment() {
        var request = new RecordOrderPaymentRequest(UUID.randomUUID(), BigDecimal.ONE,
                RecordOrderPaymentRequest.Method.CASH, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "accountant", null, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentController.record(UUID.randomUUID(), request))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
