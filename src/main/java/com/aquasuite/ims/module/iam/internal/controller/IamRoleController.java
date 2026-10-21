package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.payload.response.RoleResponse;
import com.aquasuite.ims.module.iam.internal.service.IamRoleService;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iam/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHOP_OWNER')")
public class IamRoleController {

    private final IamRoleService iamRoleService;

    @GetMapping
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.ok(iamRoleService.listCanonicalRoles());
    }

    @GetMapping("/{roleId}")
    public ApiResponse<RoleResponse> getRoleDetail(@PathVariable UUID roleId) {
        return ApiResponse.ok(iamRoleService.getRoleDetail(roleId));
    }

    @PutMapping("/{roleId}/permissions/{permissionId}")
    public ApiResponse<RoleResponse> assignPermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId
    ) {
        return ApiResponse.ok(iamRoleService.assignPermissionToRole(roleId, permissionId));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ApiResponse<RoleResponse> unassignPermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId
    ) {
        return ApiResponse.ok(iamRoleService.unassignPermissionFromRole(roleId, permissionId));
    }
}
