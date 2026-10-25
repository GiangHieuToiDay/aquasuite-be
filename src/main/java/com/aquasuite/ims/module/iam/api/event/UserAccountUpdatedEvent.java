package com.aquasuite.ims.module.iam.api.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record UserAccountUpdatedEvent(UUID eventId, UUID actorId, UUID userId,
                                      Map<String, Object> oldData, Map<String, Object> newData, Instant occurredAt) {

    public UserAccountUpdatedEvent {
        oldData = oldData == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(oldData));
        newData = newData == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(newData));
    }

    public UserAccountUpdatedEvent(UUID actorId, UUID userId, Map<String, Object> oldData, Map<String, Object> newData) {
        this(UUID.randomUUID(), actorId, userId, oldData, newData, Instant.now());
    }
}
