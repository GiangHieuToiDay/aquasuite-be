package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.UUID;

public interface IamPermissionService {

    PageResponse<PermissionResponse> listPermissions(PageQuery pageQuery, String search);

    PermissionResponse getPermissionDetail(UUID permissionId);

    PermissionResponse createPermission(CreatePermissionRequest request);

    PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request);

    void deletePermission(UUID permissionId);
}
