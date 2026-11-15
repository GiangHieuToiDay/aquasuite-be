package com.aquasuite.ims.module.servicedesk.internal.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng tách rời phụ thuộc tới module Fulfillment (UC-41 chưa triển khai).
 * Khi Fulfillment hoàn thiện, chỉ cần thay implementation stub bằng adapter gọi {@code FulfillmentApi}.
 */
public interface FulfillmentWarrantyPort {

    Optional<DeliveryNoteSnapshot> findDeliverySummary(UUID deliveryId);

    record DeliveryNoteSnapshot(
            UUID deliveryId,
            String deliveryCode,
            String status,
            OffsetDateTime deliveredAt,
            String note
    ) {
    }
}

