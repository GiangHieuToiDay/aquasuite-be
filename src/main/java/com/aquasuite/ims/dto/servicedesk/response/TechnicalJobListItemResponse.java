package com.aquasuite.ims.dto.servicedesk.response;

import com.aquasuite.ims.enums.servicedesk.TechnicalJobStatus;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Một dòng công việc kỹ thuật hiển thị trên màn tra cứu/lịch làm việc (UC-59).
 */
@Builder
public record TechnicalJobListItemResponse(
        UUID technicalJobId,
        UUID customerId,
        UUID technicianId,
        TechnicalJobType jobType,
        TechnicalJobStatus status,
        String serviceAddress,
        String issueDescription,
        OffsetDateTime scheduledAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt) {
}
