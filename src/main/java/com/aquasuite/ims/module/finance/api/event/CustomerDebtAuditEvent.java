package com.aquasuite.ims.module.finance.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable audit intent emitted by Finance after a successful UC-24 transaction. */
public record CustomerDebtAuditEvent(
        UUID eventId,
        UUID actorId,
        UUID entityId,
        String entityType,
        String action,
        Map<String, Object> oldData,
        Map<String, Object> newData,
        String reason,
        Instant occurredAt) {}
