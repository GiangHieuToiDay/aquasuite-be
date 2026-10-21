package com.aquasuite.ims.module.iam.internal.mapper;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionPageResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;
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

    default PermissionPageResponse toPageResponse(Page<Permission> page) {
        Objects.requireNonNull(page, "page must not be null");
        List<PermissionResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PermissionPageResponse.builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
