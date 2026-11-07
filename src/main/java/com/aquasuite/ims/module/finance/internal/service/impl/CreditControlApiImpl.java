package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.CreditCheckResponse;
import com.aquasuite.ims.module.finance.internal.service.CustomerDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Triển khai hợp đồng kiểm tra khách và tín dụng cho module Sales. */
@Service
@RequiredArgsConstructor
public class CreditControlApiImpl implements CreditControlApi {
    /** Cung cấp kiểm tra tình trạng khách và công nợ. */
    private final CustomerDebtService customerDebtService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Dùng transaction của Sales để giữ khóa khách đến khi việc tạo đơn kết thúc.
     * @param customerId mã khách cần kiểm tra
     */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireCustomerOrderAllowed(UUID customerId) {
        customerDebtService.requireCustomerOrderAllowed(customerId);
    }

    /**
     * Chuyển yêu cầu từ Sales sang kiểm tra công nợ và trả lại kết quả được phép hay không.
     * @param command thông tin khách, đơn và số tiền cần kiểm tra
     * @return quyết định tín dụng và hạn mức còn khả dụng
     */
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
