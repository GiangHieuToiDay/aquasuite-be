package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.PaymentAllocationAdjustmentApi;
import com.aquasuite.ims.module.finance.api.dto.OrderModificationSettlementCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapter that preserves the Finance API/internal boundary for UC-26. */
@Service
@RequiredArgsConstructor
public class PaymentAllocationAdjustmentApiImpl implements PaymentAllocationAdjustmentApi {
    private final PaymentAllocationService paymentAllocationService;

    @Override
    public void reconcileOrderModification(OrderModificationSettlementCommand command) {
        paymentAllocationService.reconcileOrderModification(command);
    }
}
