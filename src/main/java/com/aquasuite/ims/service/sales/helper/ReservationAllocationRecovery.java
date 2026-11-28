package com.aquasuite.ims.service.sales.helper;
import com.aquasuite.ims.service.sales.OrderReservationService;

import com.aquasuite.ims.repository.sales.store.ReservationAllocationStore;
import com.aquasuite.ims.dto.sales.response.OrderStockReservationView;
import com.aquasuite.ims.exception.sales.ProjectDepositErrorCode;
import com.aquasuite.ims.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationAllocationRecovery {
    private final OrderReservationService reservations;
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
