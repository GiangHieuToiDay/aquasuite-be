package com.aquasuite.ims.module.servicedesk.internal.payload.res;

import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chi tiết đầy đủ một công việc kỹ thuật (UC-59 xem chi tiết; cũng dùng làm phản hồi cho UC-60/61).
 */
@Builder
public record TechnicalJobDetailResponse(
        UUID technicalJobId,
        UUID customerId,
        UUID orderId,
        UUID warrantyRecordId,
        UUID technicianId,
        UUID createdBy,
        TechnicalJobType jobType,
        TechnicalJobStatus status,
        String serviceAddress,
        String issueDescription,
        String result,
        String technicalNote,
        String rejectionReason,
        OffsetDateTime scheduledAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt) {
}
