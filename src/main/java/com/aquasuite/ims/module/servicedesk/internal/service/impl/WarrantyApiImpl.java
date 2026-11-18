package com.aquasuite.ims.module.servicedesk.internal.service.impl;

import com.aquasuite.ims.module.servicedesk.api.WarrantyApi;
import com.aquasuite.ims.module.servicedesk.api.dto.WarrantyEligibilityDto;
import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyRecord;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyDisplayStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import com.aquasuite.ims.module.servicedesk.internal.error.ServicedeskErrorCode;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import com.aquasuite.ims.module.servicedesk.internal.repository.WarrantyRecordRepository;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarrantyApiImpl implements WarrantyApi {

    private static final List<TechnicalJobStatus> OPEN_REPAIR_JOB_STATUSES =
            List.of(TechnicalJobStatus.ASSIGNED, TechnicalJobStatus.IN_PROGRESS);

    private final WarrantyRecordRepository warrantyRecordRepository;
    private final TechnicalJobRepository technicalJobRepository;

    @Qualifier("servicedeskClock")
    private final Clock servicedeskClock;

    @Override
    @Transactional(readOnly = true)
    public boolean isEligible(UUID warrantyRecordId) {
        return getEligibility(warrantyRecordId).eligible();
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyEligibilityDto getEligibility(UUID warrantyRecordId) {
        WarrantyRecord record = warrantyRecordRepository.findById(warrantyRecordId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_NOT_FOUND, warrantyRecordId));

        LocalDate today = LocalDate.now(servicedeskClock);
        boolean hasOpenJob = technicalJobRepository.existsByWarrantyRecordIdAndJobTypeAndStatusIn(
                warrantyRecordId, TechnicalJobType.WARRANTY_REPAIR, OPEN_REPAIR_JOB_STATUSES);
        WarrantyDisplayStatus displayStatus = record.computeDisplayStatus(today, hasOpenJob);

        boolean eligible = record.getStatus() == WarrantyStatus.ACTIVE
                && record.getEndDate() != null
                && !today.isAfter(record.getEndDate());

        return WarrantyEligibilityDto.builder()
                .warrantyRecordId(record.getWarrantyRecordId())
                .warrantyCode(record.getWarrantyCode())
                .eligible(eligible)
                .displayStatus(displayStatus.name())
                .startDate(record.getStartDate())
                .endDate(record.getEndDate())
                .remainingDays(record.computeRemainingDays(today))
                .voidReason(record.getVoidReason())
                .build();
    }
}

