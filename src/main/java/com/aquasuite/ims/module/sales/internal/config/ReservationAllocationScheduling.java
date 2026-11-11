package com.aquasuite.ims.module.sales.internal.config;

import com.aquasuite.ims.module.sales.internal.repository.ReservationAllocationStore;
import com.aquasuite.ims.module.sales.internal.service.ReservationAllocationRunner;
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
