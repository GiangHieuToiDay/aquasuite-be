package com.aquasuite.ims.module.iam.api.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record UserAccountCreatedEvent(UUID eventId, UUID actorId, UUID userId,
                                      Map<String, Object> data, Instant occurredAt) {

    public UserAccountCreatedEvent {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public UserAccountCreatedEvent(UUID actorId, UUID userId, Map<String, Object> data) {
        this(UUID.randomUUID(), actorId, userId, data, Instant.now());
    }
}
