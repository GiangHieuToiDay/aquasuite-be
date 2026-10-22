package com.aquasuite.ims.module.iam.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserAccountCreatedEvent(UUID eventId, UUID actorId, UUID userId,
                                      Map<String, Object> data, Instant occurredAt) {
    public UserAccountCreatedEvent(UUID actorId, UUID userId, Map<String, Object> data) {
        this(UUID.randomUUID(), actorId, userId, Map.copyOf(data), Instant.now());
    }
}
