package com.aquasuite.ims.event.servicedesk;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sự kiện kiểm toán khi tạo hoặc hủy yêu cầu bảo hành (UC-47), được module Audit ghi nhận.
 */
public record WarrantyTicketAuditEvent(
        UUID eventId,
        UUID ticketId,
        UUID actorId,
        String action,
        Map<String, Object> oldData,
        Map<String, Object> newData,
        OffsetDateTime occurredAt
) {
}

