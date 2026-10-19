package com.aquasuite.ims.module.audit.api;

import com.aquasuite.ims.module.audit.api.dto.AuditCommand;

import java.util.UUID;

public interface AuditApi {
    UUID append(AuditCommand command);
}
