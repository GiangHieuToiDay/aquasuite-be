package com.aquasuite.ims.config.servicedesk;

import com.aquasuite.ims.service.servicedesk.WarrantyAutoIssuanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Lịch chạy nền đồng bộ cấp thẻ bảo hành tự động cho các đơn hàng hoàn thành và phiếu giao đã giao.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnExpression("'${aquasuite.config.environment:default}' != 'test' and ${aquasuite.servicedesk.auto-warranty.enabled:true}")
public class WarrantyAutoIssuanceScheduler {

    private final WarrantyAutoIssuanceService warrantyAutoIssuanceService;

    @Scheduled(
            fixedDelayString = "${aquasuite.servicedesk.auto-warranty.delay-ms:15000}",
            initialDelayString = "${aquasuite.servicedesk.auto-warranty.initial-delay-ms:10000}"
    )
    public void syncPendingWarranties() {
        try {
            warrantyAutoIssuanceService.syncEligibleRecords();
        } catch (RuntimeException ex) {
            log.warn("Scheduled warranty auto-issuance failed: {}", ex.getMessage());
        }
    }
}
