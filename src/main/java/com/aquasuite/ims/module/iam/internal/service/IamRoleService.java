package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.response.RoleOptionResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface IamRoleService {

    /** Liệt kê vai trò đang hoạt động có thể gán cho tài khoản. */
    List<RoleOptionResponse> listAssignableRoles();

    /** Liệt kê các vai trò chuẩn theo thứ tự hiển thị ổn định. */
    List<RoleResponse> listCanonicalRoles();

    /** Lấy vai trò và tập quyền hiện hành theo ID. */
    RoleResponse getRoleDetail(UUID roleId);

    /** Gán quyền đang hoạt động vào một vai trò chuẩn. */
    RoleResponse assignPermissionToRole(UUID roleId, UUID permissionId);

    /** Gỡ quyền khỏi vai trò nhưng không xóa bản ghi quyền. */
    RoleResponse unassignPermissionFromRole(UUID roleId, UUID permissionId);
}
