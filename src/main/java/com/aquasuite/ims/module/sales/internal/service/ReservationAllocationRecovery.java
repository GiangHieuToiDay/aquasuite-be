package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderStockReservationView;
import com.aquasuite.ims.module.sales.internal.error.ProjectDepositErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationAllocationRecovery {
    private final OrderStockReservationService reservations;
    private final ReservationAllocationStore jobs;
    private final ReservationAllocationRunner runner;

    public OrderStockReservationView retry(UUID orderId, UUID allocationId) {
        reservations.get(orderId); // Authorize the requesting staff before exposing a job or retrying.
        var job = jobs.find(allocationId).filter(j -> orderId.equals(j.orderId()))
                .orElseThrow(() -> new AppException(ProjectDepositErrorCode.INVALID_REQUEST));
        runner.attempt(job.id());
        return reservations.get(orderId);
    }
}
