package com.aquasuite.ims.dto.servicedesk.request;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command nội bộ để tạo thẻ bảo hành tự động (idempotent) khi nhận sự kiện hoàn tất giao hàng từ Fulfillment.
 */
public record CreateDeliveryWarrantyCommand(
        UUID deliveryId,
        UUID orderItemId,
        String serialNumber,
        LocalDate deliveryDate,
        UUID actorId
) {
}

