package com.aquasuite.ims.module.finance.api;

import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;

/** Final synchronous credit decision consumed by the Sales order-write transaction. */
public interface CreditControlApi {
    CreditAuthorizationDecision authorize(CreditAuthorizationCommand command);
}
