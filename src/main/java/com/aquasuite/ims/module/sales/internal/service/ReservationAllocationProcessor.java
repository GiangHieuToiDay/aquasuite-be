package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.repository.OrderStockReservationStore;
import com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationAllocationProcessor {
    private final ReservationAllocationStore jobs;
    private final OrderStockReservationStore selections;
    private final OrderStockReservationService reservations;

    /** Lock order before job/stock, matching finance's order-first locking protocol. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void process(UUID id) {
        var first = jobs.find(id);
        if (first.isEmpty() || "DONE".equals(first.get().status())) return;
        selections.lockOrder(first.get().orderId());
        var job = jobs.lock(id);
        if (job.isEmpty() || "DONE".equals(job.get().status())) return;
        reservations.allocateAutomatically(job.get().orderId(), id);
        jobs.done(id);
    }
}
