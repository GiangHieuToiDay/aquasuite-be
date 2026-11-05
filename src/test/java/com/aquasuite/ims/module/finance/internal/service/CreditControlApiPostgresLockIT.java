package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.CreditCheckResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requires Docker; run explicitly with -Dtest=CreditControlApiPostgresLockIT. */
class CreditControlApiPostgresLockIT {

    @Test
    void sameCustomerCreditDecisionsSerializeUntilFirstTransactionCompletes() throws Exception {
        // PostgreSQL does not recognize the legacy JVM default "Asia/Saigon" as a session zone.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17.6")) {
            database.start();
            DataSource dataSource = dataSource(database);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            CountDownLatch firstCheckEntered = new CountDownLatch(1);
            CountDownLatch releaseFirstCheck = new CountDownLatch(1);
            AtomicInteger checks = new AtomicInteger();

            CustomerDebtService debtService = new CustomerDebtService() {
                @Override
                public CreditCheckResponse checkCredit(UUID customerId, CreditCheckRequest request) {
                    if (checks.incrementAndGet() == 1) {
                        firstCheckEntered.countDown();
                        try {
                            assertTrue(releaseFirstCheck.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }
                    return CreditCheckResponse.builder().customerId(customerId).approved(true)
                            .reasonCode("APPROVED").availableCredit(BigDecimal.TEN).build();
                }

                @Override public com.aquasuite.ims.module.finance.internal.payload.response.CustomerDebtPageResponse list(String keyword, String customerType, String debtStatus, int page, int size) { throw new UnsupportedOperationException(); }
                @Override public com.aquasuite.ims.module.finance.internal.payload.response.DebtDashboardStatsResponse stats() { throw new UnsupportedOperationException(); }
                @Override public com.aquasuite.ims.module.finance.internal.payload.response.CustomerDebtDetailResponse detail(UUID customerId) { throw new UnsupportedOperationException(); }
                @Override public com.aquasuite.ims.module.finance.internal.payload.response.BlacklistStatusResponse blacklistStatus(UUID customerId) { throw new UnsupportedOperationException(); }
                @Override public void approveOverride(UUID orderId, com.aquasuite.ims.module.finance.internal.payload.request.ReasonRequest request, UUID actorId) { throw new UnsupportedOperationException(); }
                @Override public com.aquasuite.ims.module.finance.internal.payload.response.DebtReminderResponse sendReminder(UUID customerId, com.aquasuite.ims.module.finance.internal.payload.request.DebtReminderRequest request, UUID actorId) { throw new UnsupportedOperationException(); }
                @Override public void saveCallResult(UUID customerId, com.aquasuite.ims.module.finance.internal.payload.request.DebtCallResultRequest request, UUID actorId) { throw new UnsupportedOperationException(); }
                @Override public com.aquasuite.ims.module.finance.internal.pdf.StatementDocument exportStatement(UUID customerId, UUID actorId) { throw new UnsupportedOperationException(); }
                @Override public com.aquasuite.ims.module.finance.internal.payload.response.StatementEmailResponse emailStatement(UUID customerId, UUID actorId) { throw new UnsupportedOperationException(); }
                @Override public void block(UUID customerId, com.aquasuite.ims.module.finance.internal.payload.request.ReasonRequest request, UUID actorId) { throw new UnsupportedOperationException(); }
                @Override public void restore(UUID customerId, com.aquasuite.ims.module.finance.internal.payload.request.ReasonRequest request, UUID actorId) { throw new UnsupportedOperationException(); }
            };

            CreditControlApiImpl api = new CreditControlApiImpl(debtService, new JdbcTemplate(dataSource));
            CreditAuthorizationCommand command = new CreditAuthorizationCommand(UUID.randomUUID(), UUID.randomUUID(), "COUNTER", BigDecimal.ONE);

            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> api.authorize(command)));
                boolean firstEntered = firstCheckEntered.await(5, TimeUnit.SECONDS);
                if (!firstEntered) {
                    first.get(5, TimeUnit.SECONDS);
                }
                assertTrue(firstEntered);
                Future<?> second = executor.submit(() -> transaction.executeWithoutResult(status -> api.authorize(command)));

                Thread.sleep(250);
                assertEquals(1, checks.get());
                releaseFirstCheck.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                assertEquals(2, checks.get());
            }
        }
    }

    private DataSource dataSource(PostgreSQLContainer<?> database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(database.getJdbcUrl());
        dataSource.setUsername(database.getUsername());
        dataSource.setPassword(database.getPassword());
        return dataSource;
    }
}
