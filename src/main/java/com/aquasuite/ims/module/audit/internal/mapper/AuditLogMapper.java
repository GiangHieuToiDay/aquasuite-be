package com.aquasuite.ims.module.audit.internal.mapper;

import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditLogMapper {

    @Mapping(source = "auditId", target = "id")
    @Mapping(source = "userId", target = "actorId")
    AuditLogResponse toResponse(AuditLog source);
}
