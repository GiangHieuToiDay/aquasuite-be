package com.aquasuite.ims.dto.servicedesk.response;

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

