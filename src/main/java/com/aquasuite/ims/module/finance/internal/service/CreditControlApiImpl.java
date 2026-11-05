package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.CreditCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditControlApiImpl implements CreditControlApi {
    private final CustomerDebtService customerDebtService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public CreditAuthorizationDecision authorize(CreditAuthorizationCommand command) {
        lockCustomerCredit(command.customerId());
        CreditCheckResponse result = customerDebtService.checkCredit(command.customerId(),
                new CreditCheckRequest(command.orderId(), command.orderChannel(), command.orderAmount()));
        return new CreditAuthorizationDecision(result.approved(), result.reasonCode(), result.availableCredit());
    }

    /**
     * Serializes final credit decisions for one customer until the caller's surrounding
     * order-write transaction commits or rolls back. This prevents concurrent UC-21
     * order confirmations from both consuming the same remaining credit.
     */
    private void lockCustomerCredit(java.util.UUID customerId) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?)::bigint)",
                statement -> statement.setString(1, "customer-credit:" + customerId),
                resultSet -> null);
    }
}
