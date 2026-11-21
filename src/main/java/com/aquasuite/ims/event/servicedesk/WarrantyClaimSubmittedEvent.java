package com.aquasuite.ims.event.servicedesk;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sự kiện Domain Event phát ra khi khách hàng gửi thành công yêu cầu bảo hành trực tuyến (UC-47).
 * Được sử dụng bởi các module khác (kỹ thuật, thông báo) khi cần lắng nghe và xử lý tiếp.
 */
public record WarrantyClaimSubmittedEvent(
        UUID eventId,
        UUID ticketId,
        String ticketCode,
        UUID warrantyRecordId,
        UUID customerId,
        String issueDescription,
        String siteAddress,
        LocalDate preferredDate,
        String preferredSlot,
        String contactName,
        String contactPhone,
        List<String> mediaUrls,
        OffsetDateTime createdAt
) {
}

