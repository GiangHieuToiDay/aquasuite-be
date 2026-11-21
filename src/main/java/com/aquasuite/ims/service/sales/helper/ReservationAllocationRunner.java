package com.aquasuite.ims.service.sales.helper;

import com.aquasuite.ims.repository.sales.store.ReservationAllocationStore;
import com.aquasuite.ims.exception.AppException;
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
