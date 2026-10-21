package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface IamRoleService {

    List<RoleResponse> listCanonicalRoles();

    RoleResponse getRoleDetail(UUID roleId);

    RoleResponse assignPermissionToRole(UUID roleId, UUID permissionId);

    RoleResponse unassignPermissionFromRole(UUID roleId, UUID permissionId);
}
