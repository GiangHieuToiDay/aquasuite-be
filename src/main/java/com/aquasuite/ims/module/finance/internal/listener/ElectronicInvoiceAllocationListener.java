package com.aquasuite.ims.module.finance.internal.listener;

import com.aquasuite.ims.module.finance.internal.service.PaymentAllocationService;
import com.aquasuite.ims.module.sales.api.event.ElectronicInvoiceLinkedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Re-evaluates verified receipts as soon as an invoice becomes a legal electronic-invoice target.
 * The UC-63 order snapshot determines whether all receipts or only the deposit cap can be automatic.
 * It runs before commit so linking and allocations either commit together or both roll back for retry.
 */
@Component
@RequiredArgsConstructor
public class ElectronicInvoiceAllocationListener {

    private final PaymentAllocationService paymentAllocationService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onElectronicInvoiceLinked(ElectronicInvoiceLinkedEvent event) {
        paymentAllocationService.autoAllocateVerifiedReceiptsForOrder(event.orderId(), event.linkedBy());
    }
}
