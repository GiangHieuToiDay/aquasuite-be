package com.aquasuite.ims.module.finance.api;

import com.aquasuite.ims.module.finance.api.dto.OrderModificationSettlementCommand;

/** Public Finance contract used by UC-26 after its tax/refund result has been recorded. */
public interface PaymentAllocationAdjustmentApi {
    void reconcileOrderModification(OrderModificationSettlementCommand command);
}
