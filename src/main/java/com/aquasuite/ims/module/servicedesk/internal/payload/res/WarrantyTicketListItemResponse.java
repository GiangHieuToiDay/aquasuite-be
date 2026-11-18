package com.aquasuite.ims.module.servicedesk.internal.payload.res;

import com.aquasuite.ims.module.servicedesk.internal.enums.PreferredSlot;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyTicketStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mục hiển thị trong danh sách yêu cầu bảo hành của khách hàng (UC-47).
 */
@Builder
public record WarrantyTicketListItemResponse(
        UUID ticketId,
        String ticketCode,
        UUID warrantyRecordId,
        String warrantyCode,
        String productName,
        String variantName,
        String serialNumber,
        String issueDescription,
        LocalDate preferredDate,
        PreferredSlot preferredSlot,
        String siteAddress,
        WarrantyTicketStatus status,
        boolean canCancel,
        int mediaCount,
        OffsetDateTime createdAt
) {
}

