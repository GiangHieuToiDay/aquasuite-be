package com.aquasuite.ims.module.iam.internal.mapper;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import java.util.Objects;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface PermissionMapper {

    PermissionResponse mapPermission(Permission permission);

    @Named("toResponse")
    default PermissionResponse toResponse(Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        return mapPermission(permission);
    }
}
