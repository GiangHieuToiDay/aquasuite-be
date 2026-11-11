package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderStockReservationView;
import com.aquasuite.ims.module.sales.internal.error.ProjectDepositErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationAllocationRunner {
    private final ReservationAllocationProcessor processor;
    private final ReservationAllocationStore jobs;
    private final OrderStockReservationService reservations;

    public OrderStockReservationView retry(UUID orderId, UUID allocationId) {
        reservations.get(orderId); // Authorize before exposing a job or retrying.
        var job = jobs.find(allocationId).filter(j -> orderId.equals(j.orderId()))
                .orElseThrow(() -> new AppException(ProjectDepositErrorCode.INVALID_REQUEST));
        attempt(job.id());
        return reservations.get(orderId);
    }

    /** Runs outside the payment transaction; allocation failure must not undo verified money. */
    public void attempt(UUID id) {
        try {
            processor.process(id);
        } catch (RuntimeException failure) {
            String code = failure instanceof AppException business
                    ? business.getErrorCode().getCode() : "SALES_DEPOSIT_006";
            log.warn("Deposit allocation {} failed with {}",id,code);
            try { jobs.failed(id,code); }
            catch (RuntimeException unavailable) {
                // The original durable PENDING row remains eligible for restart/retry recovery.
                log.warn("Could not record allocation failure for {}",id);
            }
        }
    }
}
