package com.aquasuite.ims.event.iam;

import java.time.Instant;
import java.util.UUID;

public record UserAccountStatusChangedEvent(UUID eventId, UUID actorId, UUID userId,
                                             String oldStatus, String newStatus, String reason, Instant occurredAt) {
    public UserAccountStatusChangedEvent(UUID actorId, UUID userId, String oldStatus, String newStatus, String reason) {
        this(UUID.randomUUID(), actorId, userId, oldStatus, newStatus, reason, Instant.now());
    }
}
