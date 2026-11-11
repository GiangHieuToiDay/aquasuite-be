package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.service.impl.CreditControlApiImpl;

import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.CreditCheckResponse;
import com.aquasuite.ims.module.finance.internal.service.impl.CreditControlApiImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditControlApiImplTest {

    @Mock
    private CustomerDebtService customerDebtService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CreditControlApiImpl creditControlApi;

    @Test
    void acquiresCustomerAdvisoryLockBeforeRunningFinalCreditCheck() {
        UUID customerId = UUID.randomUUID();
        CreditAuthorizationCommand command = new CreditAuthorizationCommand(
                customerId, UUID.randomUUID(), "COUNTER", BigDecimal.TEN);
        when(customerDebtService.checkCredit(eq(customerId), any(CreditCheckRequest.class)))
                .thenReturn(CreditCheckResponse.builder()
                        .customerId(customerId)
                        .approved(true)
                        .reasonCode("APPROVED")
                        .availableCredit(BigDecimal.TEN)
                        .build());

        var decision = creditControlApi.authorize(command);

        assertTrue(decision.approved());
        InOrder order = inOrder(jdbcTemplate, customerDebtService);
        order.verify(jdbcTemplate).query(
                eq("SELECT pg_advisory_xact_lock(hashtext(?)::bigint)"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class));
        order.verify(customerDebtService).checkCredit(eq(customerId), any(CreditCheckRequest.class));
    }
}
