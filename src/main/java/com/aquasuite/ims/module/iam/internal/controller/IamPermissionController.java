package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.module.iam.internal.service.IamPermissionService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iam/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHOP_OWNER')")
public class IamPermissionController {

    private final IamPermissionService iamPermissionService;

    @GetMapping
    public ApiResponse<PageResponse<PermissionResponse>> listPermissions(
            @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String search
    ) {
        return ApiResponse.ok(iamPermissionService.listPermissions(pageQuery, search));
    }

    @GetMapping("/{permissionId}")
    public ApiResponse<PermissionResponse> getPermissionDetail(@PathVariable UUID permissionId) {
        return ApiResponse.ok(iamPermissionService.getPermissionDetail(permissionId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request
    ) {
        PermissionResponse created = iamPermissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{permissionId}")
    public ApiResponse<PermissionResponse> updatePermission(
            @PathVariable UUID permissionId,
            @Valid @RequestBody UpdatePermissionRequest request
    ) {
        return ApiResponse.ok(iamPermissionService.updatePermission(permissionId, request));
    }

    @DeleteMapping("/{permissionId}")
    public ApiResponse<Void> deletePermission(@PathVariable UUID permissionId) {
        iamPermissionService.deletePermission(permissionId);
        return ApiResponse.ok(null);
    }
}
