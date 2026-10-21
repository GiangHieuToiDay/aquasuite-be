package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionPageResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;

import java.util.UUID;

public interface IamPermissionService {

    PermissionPageResponse listPermissions(int page, int size, String search, String sortBy, String direction);

    PermissionResponse getPermissionDetail(UUID permissionId);

    PermissionResponse createPermission(CreatePermissionRequest request);

    PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request);

    void deletePermission(UUID permissionId);
}
