package com.aquasuite.ims.module.crm.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Audit-safe event emitted after an approved customer credit-profile change commits. */
public record CustomerCreditProfileUpdatedEvent(
        UUID eventId,
        UUID customerId,
        UUID requestedBy,
        UUID approvedBy,
        BigDecimal previousCreditLimit,
        Integer previousCreditTermDays,
        BigDecimal creditLimit,
        Integer creditTermDays,
        String reason,
        Instant occurredAt) {}
