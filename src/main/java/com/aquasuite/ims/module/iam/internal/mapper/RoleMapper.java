package com.aquasuite.ims.module.iam.internal.mapper;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.RoleResponse;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = PermissionMapper.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface RoleMapper {

    @Mapping(source = "roleId", target = "id")
    @Mapping(source = "roleName", target = "code")
    @Mapping(source = "permissions", target = "permissions", qualifiedByName = "toSortedPermissionResponses")
    RoleResponse mapRole(Role role);

    default RoleResponse toResponse(Role role) {
        Objects.requireNonNull(role, "role must not be null");
        return mapRole(role);
    }

    @IterableMapping(qualifiedByName = "toResponse")
    List<PermissionResponse> mapPermissionSet(Set<Permission> permissions);

    @Named("toSortedPermissionResponses")
    default List<PermissionResponse> toSortedPermissionResponses(Set<Permission> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return mapPermissionSet(permissions).stream()
                .sorted(Comparator.comparing(PermissionResponse::getCode))
                .toList();
    }
}
