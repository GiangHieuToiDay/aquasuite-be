package com.aquasuite.ims.module.audit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditCommand(
        UUID actorId,
        String action,
        UUID aggregateId,
        JsonNode before,
        JsonNode after,
        Instant occurredAt) {}
