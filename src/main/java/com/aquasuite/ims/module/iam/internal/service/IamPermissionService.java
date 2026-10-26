package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.UUID;

public interface IamPermissionService {

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
