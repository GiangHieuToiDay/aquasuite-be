package com.aquasuite.ims.service.audit;

import com.aquasuite.ims.dto.audit.response.AuditLogResponse;

import java.util.List;
import java.util.UUID;

/** Public read-only audit contract for other modules. */
public interface AuditLogService {
    List<AuditLogResponse> findByEntity(String entityType, UUID entityId);
}
