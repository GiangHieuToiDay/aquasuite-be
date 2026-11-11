package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.event.OrderPaymentRecorded;
import com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DepositReservationPaymentListener {
    private final ReservationAllocationStore jobs;
    private final ReservationAllocationRunner runner;

    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void persist(OrderPaymentRecorded event) {
        jobs.enqueue(event.eventId(), event.orderId());
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void allocate(OrderPaymentRecorded event) {
        runner.attempt(event.eventId());
    }
}
