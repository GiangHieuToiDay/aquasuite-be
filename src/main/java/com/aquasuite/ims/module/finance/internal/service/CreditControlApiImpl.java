package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.CreditCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditControlApiImpl implements CreditControlApi {
    private final CustomerDebtService customerDebtService;

    @Override
    @Transactional
    public CreditAuthorizationDecision authorize(CreditAuthorizationCommand command) {
        CreditCheckResponse result = customerDebtService.checkCredit(command.customerId(),
                new CreditCheckRequest(command.orderId(), command.orderChannel(), command.orderAmount()));
        return new CreditAuthorizationDecision(result.approved(), result.reasonCode(), result.availableCredit());
    }
}
