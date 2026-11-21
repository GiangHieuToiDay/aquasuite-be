package com.aquasuite.ims.event.catalog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductVariantChangedEvent(UUID eventId, UUID actorId, UUID variantId, String action,
                                         Map<String, Object> oldData, Map<String, Object> newData,
                                         String reason, Instant occurredAt) {
    public ProductVariantChangedEvent(UUID actorId, UUID variantId, String action, Map<String, Object> oldData,
                                      Map<String, Object> newData, String reason) {
        this(UUID.randomUUID(), actorId, variantId, action, oldData, newData, reason, Instant.now());
    }
}
