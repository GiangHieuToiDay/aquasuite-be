package com.aquasuite.ims.controller.finance;

import com.aquasuite.ims.service.finance.ProjectDepositPaymentService.*;
import com.aquasuite.ims.config.finance.dev.DemoProjectDepositPaymentService;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DemoProjectDepositPaymentApiTest {
    @Test
    void allMethodsStartPendingAndVerifiedPaymentsCannotBeRewritten() {
        var api = new DemoProjectDepositPaymentService();
        UUID order = UUID.randomUUID();
        for (Method method : Method.values()) {
            var command = new DepositCommand(UUID.randomUUID(), order, UUID.randomUUID(), BigDecimal.TEN,
                    method, method == Method.BANK_TRANSFER ? "demo-bank-ref" : null, null, UUID.randomUUID());
            var payment = api.recordDeposit(command);
            assertThat(payment.status()).isEqualTo(Status.PENDING);
            api.verify(payment.paymentId(), Status.VERIFIED);
            assertThat(api.recordDeposit(command).status()).isEqualTo(Status.VERIFIED);
            assertThatThrownBy(() -> api.verify(payment.paymentId(), Status.REJECTED)).isInstanceOf(AppException.class);
        }
        assertThat(api.getPayments(order)).hasSize(2);
    }

    @Test
    void bankReferenceCannotCreditAnotherOrderAndSameRequestCannotChangeInput() {
        var api = new DemoProjectDepositPaymentService();
        UUID key = UUID.randomUUID(), order = UUID.randomUUID(), customer = UUID.randomUUID(), actor = UUID.randomUUID();
        api.recordDeposit(new DepositCommand(key, order, customer, BigDecimal.TEN, Method.BANK_TRANSFER, "ref-1", null, actor));
        assertThatThrownBy(() -> api.recordDeposit(new DepositCommand(UUID.randomUUID(), UUID.randomUUID(), customer,
                BigDecimal.TEN, Method.BANK_TRANSFER, "ref-1", null, actor))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> api.recordDeposit(new DepositCommand(key, order, customer,
                BigDecimal.ONE, Method.CASH, null, null, actor))).isInstanceOf(AppException.class);
        assertThat(api.getPayments(order)).hasSize(1);
    }
}
