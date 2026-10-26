package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.payload.response.RoleOptionResponse;
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

    /** Trả về danh sách vai trò chuẩn mà hệ thống hỗ trợ. */
    @GetMapping
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.ok(iamRoleService.listCanonicalRoles());
    }

    /** Trả về các vai trò đang hoạt động có thể gán cho tài khoản. */
    @GetMapping(params = "assignable")
    public ApiResponse<List<RoleOptionResponse>> listAssignableRoles() {
        return ApiResponse.ok(iamRoleService.listAssignableRoles());
    }

    /** Trả về chi tiết vai trò cùng các quyền đang được gán. */
    @GetMapping("/{roleId}")
    public ApiResponse<RoleResponse> getRoleDetail(@PathVariable UUID roleId) {
        return ApiResponse.ok(iamRoleService.getRoleDetail(roleId));
    }

    /** Gán một quyền cho vai trò và trả về trạng thái vai trò sau cập nhật. */
    @PutMapping("/{roleId}/permissions/{permissionId}")
    public ApiResponse<RoleResponse> assignPermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId
    ) {
        return ApiResponse.ok(iamRoleService.assignPermissionToRole(roleId, permissionId));
    }

    /** Gỡ một quyền khỏi vai trò và trả về trạng thái vai trò sau cập nhật. */
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ApiResponse<RoleResponse> unassignPermission(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId
    ) {
        return ApiResponse.ok(iamRoleService.unassignPermissionFromRole(roleId, permissionId));
    }
}
