package com.aquasuite.ims.module.iam.api;

import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;

/**
 * Public Interface của Module IAM.
 * Các module khác (Catalog, Sales, CRM, Inventory, Audit, ...) chỉ được phép gọi vào IAM thông qua Interface này.
 */
public interface IamApi {

    /**
     * Lấy thông tin người dùng hiện tại đang đăng nhập.
     */
    UserSummaryDto getCurrentUser();

    /**
     * Lấy ID của người dùng hiện tại đang đăng nhập.
     */
    Long getCurrentUserId();

    /**
     * Kiểm tra xem người dùng hiện tại có quyền Admin hay không.
     */
    boolean isAdmin();

    /**
     * Xác thực quyền Admin của người dùng hiện tại.
     * Ném AppException(CommonErrorCode.ACCESS_DENIED) nếu không phải Admin.
     */
    void validateAdminAccess();
}
