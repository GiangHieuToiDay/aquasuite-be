package com.aquasuite.ims.event.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Audit-safe lifecycle event; it intentionally contains no identity-document or address value. */
public record CustomerCreditProfileRequestEvent(
        UUID eventId,
        UUID requestId,
        UUID customerId,
        UUID actorId,
        String action,
        BigDecimal requestedCreditLimit,
        Integer requestedCreditTermDays,
        boolean identityEvidenceProvided,
        Instant occurredAt) {
}
