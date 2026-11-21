package com.aquasuite.ims.controller.iam;

import com.aquasuite.ims.dto.iam.request.CreatePermissionRequest;
import com.aquasuite.ims.dto.iam.request.UpdatePermissionRequest;
import com.aquasuite.ims.dto.iam.response.PermissionResponse;
import com.aquasuite.ims.dto.iam.response.RoleOptionResponse;
import com.aquasuite.ims.dto.iam.response.RoleResponse;
import com.aquasuite.ims.service.iam.RolePermissionService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageQuery;
import com.aquasuite.ims.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHOP_OWNER')")
public class RolePermissionController {

    private final RolePermissionService service;

    // ── Roles (from IamRoleController at /api/v1/iam/roles) ──────────────────

    @GetMapping("/api/v1/iam/roles")
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.ok(service.listCanonicalRoles());
    }

    @GetMapping(value = "/api/v1/iam/roles", params = "assignable")
    public ApiResponse<List<RoleOptionResponse>> listAssignableRoles() {
        return ApiResponse.ok(service.listAssignableRoles());
    }

    @GetMapping("/api/v1/iam/roles/{roleId}")
    public ApiResponse<RoleResponse> getRoleDetail(@PathVariable UUID roleId) {
        return ApiResponse.ok(service.getRoleDetail(roleId));
    }

    @PutMapping("/api/v1/iam/roles/{roleId}/permissions/{permissionId}")
    public ApiResponse<RoleResponse> assignPermission(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        return ApiResponse.ok(service.assignPermissionToRole(roleId, permissionId));
    }

    @DeleteMapping("/api/v1/iam/roles/{roleId}/permissions/{permissionId}")
    public ApiResponse<RoleResponse> unassignPermission(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        return ApiResponse.ok(service.unassignPermissionFromRole(roleId, permissionId));
    }

    // ── Permissions (from IamPermissionController at /api/v1/iam/permissions) ─

    @GetMapping("/api/v1/iam/permissions")
    public ApiResponse<PageResponse<PermissionResponse>> listPermissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String search) {
        PageQuery pageQuery = new PageQuery(page, size, sortBy, direction);
        return ApiResponse.ok(service.listPermissions(pageQuery, search));
    }

    @GetMapping("/api/v1/iam/permissions/{permissionId}")
    public ApiResponse<PermissionResponse> getPermissionDetail(@PathVariable UUID permissionId) {
        return ApiResponse.ok(service.getPermissionDetail(permissionId));
    }

    @PostMapping("/api/v1/iam/permissions")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse response = service.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Permission created successfully"));
    }

    @PatchMapping("/api/v1/iam/permissions/{permissionId}")
    public ApiResponse<PermissionResponse> updatePermission(
            @PathVariable UUID permissionId,
            @Valid @RequestBody UpdatePermissionRequest request) {
        return ApiResponse.ok(service.updatePermission(permissionId, request), "Permission updated successfully");
    }

    @DeleteMapping("/api/v1/iam/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable UUID permissionId) {
        service.deletePermission(permissionId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Permission deleted successfully"));
    }
}
