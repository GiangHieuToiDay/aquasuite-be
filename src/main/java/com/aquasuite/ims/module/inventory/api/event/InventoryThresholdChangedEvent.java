package com.aquasuite.ims.module.inventory.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi ngưỡng tồn an toàn (min/max) của một SKU được cấu hình lại trong UC-31.
 * Module Audit lắng nghe sự kiện này để ghi lại người thực hiện cùng giá trị trước/sau,
 * phục vụ truy vết các hành động nhạy cảm (GB-06/GB-26).
 *
 * @param eventId       khóa idempotent cho bản ghi audit
 * @param actorId       người thực hiện thay đổi
 * @param variantId     SKU bị thay đổi ngưỡng
 * @param oldMinimumQty ngưỡng tối thiểu trước khi thay đổi
 * @param oldMaximumQty ngưỡng tối đa trước khi thay đổi (có thể null)
 * @param newMinimumQty ngưỡng tối thiểu sau khi thay đổi
 * @param newMaximumQty ngưỡng tối đa sau khi thay đổi (có thể null)
 * @param occurredAt    thời điểm thay đổi
 */
public record InventoryThresholdChangedEvent(
        UUID eventId,
        UUID actorId,
        UUID variantId,
        BigDecimal oldMinimumQty,
        BigDecimal oldMaximumQty,
        BigDecimal newMinimumQty,
        BigDecimal newMaximumQty,
        Instant occurredAt) {

    public InventoryThresholdChangedEvent(UUID actorId, UUID variantId,
                                          BigDecimal oldMinimumQty, BigDecimal oldMaximumQty,
                                          BigDecimal newMinimumQty, BigDecimal newMaximumQty) {
        this(UUID.randomUUID(), actorId, variantId, oldMinimumQty, oldMaximumQty,
                newMinimumQty, newMaximumQty, Instant.now());
    }
}
