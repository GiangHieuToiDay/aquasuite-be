package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.common.PageQuery;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.iam.request.CreatePermissionRequest;
import com.aquasuite.ims.dto.iam.request.UpdatePermissionRequest;
import com.aquasuite.ims.dto.iam.response.PermissionResponse;
import com.aquasuite.ims.dto.iam.response.RoleOptionResponse;
import com.aquasuite.ims.dto.iam.response.RoleResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản trị RBAC: vai trò (Roles) và danh mục phân quyền (Permissions) hợp nhất.
 */
public interface RolePermissionService {

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

    /** Tìm kiếm, sắp xếp và phân trang quyền IAM. */
    PageResponse<PermissionResponse> listPermissions(PageQuery pageQuery, String search);

    /** Lấy chi tiết quyền hoặc báo không tìm thấy. */
    PermissionResponse getPermissionDetail(UUID permissionId);

    /** Chuẩn hóa và tạo một quyền mới có mã duy nhất. */
    PermissionResponse createPermission(CreatePermissionRequest request);

    /** Cập nhật nội dung quyền mà không thay đổi định danh. */
    PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request);

    /** Xóa quyền nếu không còn liên kết với vai trò. */
    void deletePermission(UUID permissionId);
}
