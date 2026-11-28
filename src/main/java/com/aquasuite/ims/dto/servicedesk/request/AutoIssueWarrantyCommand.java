package com.aquasuite.ims.dto.servicedesk.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lệnh nội bộ cấp thẻ bảo hành tự động (idempotent) từ đơn hoàn thành tại quầy hoặc phiếu giao đã hoàn tất.
 */
public record AutoIssueWarrantyCommand(
        UUID deliveryId,
        UUID orderItemId,
        String serialNumber,
        LocalDate activationDate,
        UUID actorId
) {
}
