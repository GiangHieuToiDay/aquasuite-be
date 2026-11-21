package com.aquasuite.ims.dto.servicedesk.response;

import com.aquasuite.ims.enums.servicedesk.TechnicalJobStatus;
import com.aquasuite.ims.enums.servicedesk.TechnicalJobType;
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
        UUID warrantyTicketId,
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
