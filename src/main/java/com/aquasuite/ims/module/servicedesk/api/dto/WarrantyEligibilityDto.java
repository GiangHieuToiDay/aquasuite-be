package com.aquasuite.ims.module.servicedesk.api.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record WarrantyEligibilityDto(
        UUID warrantyRecordId,
        String warrantyCode,
        boolean eligible,
        String displayStatus,
        LocalDate startDate,
        LocalDate endDate,
        long remainingDays,
        String voidReason
) {
}

