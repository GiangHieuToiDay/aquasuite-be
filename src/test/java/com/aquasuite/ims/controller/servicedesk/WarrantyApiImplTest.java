package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.entity.servicedesk.WarrantyRecord;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobStatus;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobType;
import com.aquasuite.ims.enums.servicedesk.WarrantyStatus;
import com.aquasuite.ims.repository.servicedesk.TechnicalJobRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyEligibilityTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-12T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock private WarrantyRecordRepository warrantyRecordRepository;
    @Mock private TechnicalJobRepository technicalJobRepository;

    private WarrantyRecordServiceImpl warrantyApi;

    @BeforeEach
    void setUp() {
        warrantyApi = new WarrantyRecordServiceImpl(warrantyRecordRepository, technicalJobRepository, null, null, null, null, null, null, null, null, null, FIXED_CLOCK);
    }

    @Test
    @DisplayName("Thẻ ACTIVE còn hạn và đang có job sửa chữa mở -> displayStatus CLAIMED, eligible true")
    void activeWithOpenJobReturnsClaimed() {
        UUID id = UUID.randomUUID();
        WarrantyRecord record = WarrantyRecord.builder()
                .warrantyRecordId(id)
                .warrantyCode("WAR-202611-0001")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 11, 22))
                .warrantyMonths(12)
                .status(WarrantyStatus.ACTIVE)
                .build();
        when(warrantyRecordRepository.findById(id)).thenReturn(Optional.of(record));
        when(technicalJobRepository.existsByWarrantyRecordIdAndJobTypeAndStatusIn(
                id, TechnicalJobType.WARRANTY_REPAIR, List.of(TechnicalJobStatus.ASSIGNED, TechnicalJobStatus.IN_PROGRESS)))
                .thenReturn(true);

        var dto = warrantyApi.getEligibility(id);

        assertThat(dto.eligible()).isTrue();
        assertThat(dto.displayStatus()).isEqualTo("CLAIMED");
        assertThat(dto.remainingDays()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Thẻ quá hạn -> displayStatus EXPIRED, eligible false, remainingDays 0")
    void expiredWarrantyNotEligible() {
        UUID id = UUID.randomUUID();
        WarrantyRecord record = WarrantyRecord.builder()
                .warrantyRecordId(id)
                .warrantyCode("WAR-202501-0001")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2026, 1, 1))
                .warrantyMonths(12)
                .status(WarrantyStatus.ACTIVE)
                .build();
        when(warrantyRecordRepository.findById(id)).thenReturn(Optional.of(record));
        when(technicalJobRepository.existsByWarrantyRecordIdAndJobTypeAndStatusIn(
                id, TechnicalJobType.WARRANTY_REPAIR, List.of(TechnicalJobStatus.ASSIGNED, TechnicalJobStatus.IN_PROGRESS)))
                .thenReturn(false);

        var dto = warrantyApi.getEligibility(id);

        assertThat(dto.eligible()).isFalse();
        assertThat(dto.displayStatus()).isEqualTo("EXPIRED");
        assertThat(dto.remainingDays()).isZero();
    }
}

