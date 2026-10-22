package com.aquasuite.ims.module.audit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {
    UUID id;
    UUID actorId;
    String entityType;
    UUID entityId;
    String action;
    Map<String, Object> oldData;
    Map<String, Object> newData;
    String reason;
    Instant createdAt;
}
