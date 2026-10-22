package com.aquasuite.ims.module.iam.api.event;

import java.time.Instant;
import java.util.UUID;

/** Immutable domain event emitted after a canonical IAM password update. */
public record PasswordChangedEvent(UUID eventId, UUID userId, String reason, Instant occurredAt) {

    public PasswordChangedEvent(UUID userId, String reason) {
        this(UUID.randomUUID(), userId, reason, Instant.now());
    }
}
