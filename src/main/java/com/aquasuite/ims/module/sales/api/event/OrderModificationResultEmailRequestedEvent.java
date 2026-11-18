package com.aquasuite.ims.module.sales.api.event;

import java.time.Instant;
import java.util.UUID;

/** Published after a committed UC-26 result-email request; delivery is best-effort. */
public record OrderModificationResultEmailRequestedEvent(
        UUID eventId,
        UUID requestId,
        UUID customerId,
        Instant occurredAt
) {
}
