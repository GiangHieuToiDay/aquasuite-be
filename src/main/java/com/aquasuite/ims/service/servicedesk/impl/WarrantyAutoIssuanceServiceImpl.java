package com.aquasuite.ims.service.servicedesk.impl;

import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.dto.servicedesk.request.AutoIssueWarrantyCommand;
import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.service.servicedesk.WarrantyAutoIssuanceService;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyAutoIssuanceCandidateReader;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyAutoIssuanceCandidateReader.CompletedOrderItemCandidate;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyAutoIssuanceCandidateReader.DeliveredItemCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Triển khai đồng bộ cấp thẻ bảo hành tự động theo Phương án A trong phạm vi ServiceDesk:
 * <ul>
 *     <li>Phiếu giao đã {@code DELIVERED}: kích hoạt thẻ gắn {@code deliveryId}, ngày bắt đầu theo {@code deliveredAt}.</li>
 *     <li>Đơn hàng {@code COMPLETED} không có phiếu giao (bán tại quầy): kích hoạt thẻ gắn {@code orderItemId}, ngày bắt đầu theo {@code orderDate}.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WarrantyAutoIssuanceServiceImpl implements WarrantyAutoIssuanceService {

    private static final int CANDIDATE_BATCH_LIMIT = 200;

    private final WarrantyAutoIssuanceCandidateReader candidateReader;
    private final WarrantyRecordService warrantyRecordService;
    private final CatalogWarrantyLookupService catalogWarrantyApi;

    @Qualifier("servicedeskClock")
    private final Clock servicedeskClock;

    private final ReentrantLock syncLock = new ReentrantLock();

    @Override
    public int syncEligibleRecords() {
        syncLock.lock();
        try {
            Map<UUID, Optional<CatalogWarrantyPolicyDto>> policyCache = new HashMap<>();
            int issuedCount = 0;

            issuedCount += processDeliveredDeliveries(policyCache);
            issuedCount += processCompletedOrdersWithoutDelivery(policyCache);

            if (issuedCount > 0) {
                log.info("Auto-issued {} electronic warranty record(s)", issuedCount);
            }
            return issuedCount;
        } finally {
            syncLock.unlock();
        }
    }

    private int processDeliveredDeliveries(Map<UUID, Optional<CatalogWarrantyPolicyDto>> policyCache) {
        List<DeliveredItemCandidate> candidates = candidateReader.findDeliveredDeliveryCandidates(CANDIDATE_BATCH_LIMIT);
        int issued = 0;
        for (DeliveredItemCandidate candidate : candidates) {
            CatalogWarrantyPolicyDto policy = resolveEligiblePolicy(candidate.variantId(), policyCache);
            if (policy == null) {
                continue;
            }
            LocalDate activationDate = toStoreLocalDate(candidate.deliveredAt(), candidate.updatedAt());

            if (Boolean.TRUE.equals(policy.serialTracking())) {
                Set<String> serials = normalizeSerialList(candidate.serialNumbers());
                if (serials.isEmpty()) {
                    continue;
                }
                for (String serial : serials) {
                    if (issueSafely(new AutoIssueWarrantyCommand(
                            candidate.deliveryId(),
                            candidate.orderItemId(),
                            serial,
                            activationDate,
                            null))) {
                        issued++;
                    }
                }
            } else {
                if (issueSafely(new AutoIssueWarrantyCommand(
                        candidate.deliveryId(),
                        candidate.orderItemId(),
                        null,
                        activationDate,
                        null))) {
                    issued++;
                }
            }
        }
        return issued;
    }

    private int processCompletedOrdersWithoutDelivery(Map<UUID, Optional<CatalogWarrantyPolicyDto>> policyCache) {
        List<CompletedOrderItemCandidate> candidates =
                candidateReader.findCompletedOrderWithoutDeliveryCandidates(CANDIDATE_BATCH_LIMIT);
        int issued = 0;
        for (CompletedOrderItemCandidate candidate : candidates) {
            CatalogWarrantyPolicyDto policy = resolveEligiblePolicy(candidate.variantId(), policyCache);
            if (policy == null) {
                continue;
            }
            // Đơn bán tại quầy không lưu danh sách serial trên order_items: bỏ qua để nhân viên nhập tay từ đơn hàng.
            if (Boolean.TRUE.equals(policy.serialTracking())) {
                continue;
            }
            LocalDate activationDate = toStoreLocalDate(candidate.orderDate(), candidate.updatedAt());
            if (issueSafely(new AutoIssueWarrantyCommand(
                    null,
                    candidate.orderItemId(),
                    null,
                    activationDate,
                    null))) {
                issued++;
            }
        }
        return issued;
    }

    private boolean issueSafely(AutoIssueWarrantyCommand command) {
        try {
            return warrantyRecordService.autoIssueIfEligible(command);
        } catch (RuntimeException ex) {
            log.warn("Warranty auto-issuance skipped for orderItemId={}, deliveryId={}: {}",
                    command.orderItemId(), command.deliveryId(), ex.getMessage());
            return false;
        }
    }

    private CatalogWarrantyPolicyDto resolveEligiblePolicy(
            UUID variantId,
            Map<UUID, Optional<CatalogWarrantyPolicyDto>> policyCache) {
        if (variantId == null) {
            return null;
        }
        Optional<CatalogWarrantyPolicyDto> cached = policyCache.computeIfAbsent(
                variantId,
                catalogWarrantyApi::getWarrantyInfoByVariantId
        );
        if (cached.isEmpty() || !WarrantyRecordServiceImpl.isPolicyEligible(cached.get())) {
            return null;
        }
        return cached.get();
    }

    private LocalDate toStoreLocalDate(OffsetDateTime primary, OffsetDateTime fallback) {
        Clock clock = servicedeskClock != null ? servicedeskClock : Clock.systemDefaultZone();
        ZoneId zone = clock.getZone();
        OffsetDateTime chosen = primary != null ? primary : fallback;
        return chosen != null ? chosen.atZoneSameInstant(zone).toLocalDate() : LocalDate.now(clock);
    }

    private Set<String> normalizeSerialList(List<String> serialNumbers) {
        Set<String> result = new LinkedHashSet<>();
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return result;
        }
        for (String raw : serialNumbers) {
            if (raw != null && !raw.isBlank()) {
                result.add(raw.trim());
            }
        }
        return result;
    }
}
