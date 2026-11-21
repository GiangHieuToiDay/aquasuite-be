package com.aquasuite.ims.config.sales;

import com.aquasuite.ims.repository.sales.store.ReservationAllocationStore;
import com.aquasuite.ims.service.sales.helper.ReservationAllocationRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="aquasuite.reservations.retry-enabled",havingValue="true",matchIfMissing=true)
@RequiredArgsConstructor
public class ReservationAllocationScheduling {
    private final ReservationAllocationStore jobs;
    private final ReservationAllocationRunner runner;

    @Scheduled(fixedDelayString="${aquasuite.reservations.retry-delay-ms:30000}",
            initialDelayString="${aquasuite.reservations.retry-delay-ms:30000}")
    public void recover() {
        for (var id : jobs.due()) runner.attempt(id);
    }
}
