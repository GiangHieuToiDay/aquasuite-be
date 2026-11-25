package com.aquasuite.ims.dto.servicedesk.response;

import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.enums.servicedesk.PreferredSlot;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết phiếu yêu cầu bảo hành trực tuyến (UC-47).
 */
@Builder
public record WarrantyTicketDetailResponse(
        UUID ticketId,
        String ticketCode,
        UUID warrantyRecordId,
        String warrantyCode,
        String productName,
        String variantName,
        String sku,
        String serialNumber,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        boolean warrantyExpired,
        boolean expiredAcknowledged,
        String contactName,
        String contactPhone,
        String contactEmail,
        String issueDescription,
        LocalDate preferredDate,
        PreferredSlot preferredSlot,
        String siteAddress,
        List<TicketMedia> media,
        WarrantyTicketStatus status,
        boolean canCancel,
        String result,
        String rejectionReason,
        String cancelReason,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

