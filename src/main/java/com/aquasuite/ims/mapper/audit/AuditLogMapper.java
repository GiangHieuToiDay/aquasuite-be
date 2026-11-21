package com.aquasuite.ims.mapper.audit;

import com.aquasuite.ims.dto.audit.response.AuditLogResponse;
import com.aquasuite.ims.entity.audit.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditLogMapper {

    @Mapping(source = "auditId", target = "id")
    @Mapping(source = "userId", target = "actorId")
    AuditLogResponse toResponse(AuditLog source);
}
