package com.aquasuite.ims.module.servicedesk.api.event;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sự kiện kiểm toán khi tạo hoặc hủy thẻ bảo hành (UC-45), được module Audit ghi nhận.
 */
public record WarrantyRecordAuditEvent(
        UUID eventId,
        UUID warrantyRecordId,
        UUID actorId,
        String action,
        Map<String, Object> oldData,
        Map<String, Object> newData,
        OffsetDateTime occurredAt
) {
}

