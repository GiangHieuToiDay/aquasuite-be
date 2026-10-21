package com.aquasuite.ims.module.iam.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserAccountUpdatedEvent(UUID eventId, UUID actorId, UUID userId,
                                      Map<String, Object> oldData, Map<String, Object> newData, Instant occurredAt) {
    public UserAccountUpdatedEvent(UUID actorId, UUID userId, Map<String, Object> oldData, Map<String, Object> newData) {
        this(UUID.randomUUID(), actorId, userId, Map.copyOf(oldData), Map.copyOf(newData), Instant.now());
    }
}
