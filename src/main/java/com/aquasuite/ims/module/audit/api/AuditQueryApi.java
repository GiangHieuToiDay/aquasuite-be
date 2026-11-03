package com.aquasuite.ims.module.audit.api;

import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;

import java.util.List;
import java.util.UUID;

/** Public read-only audit contract for other modules. */
public interface AuditQueryApi {
    List<AuditLogResponse> findByEntity(String entityType, UUID entityId);
}
