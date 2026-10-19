package com.aquasuite.ims.module.iam.api;

import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;

/**
 * Public Interface của Module IAM.
 * Các module khác (Sales, CRM, Inventory, Audit, ...) chỉ được phép gọi vào IAM thông qua Interface này.
 */
public interface IamApi {

    /**
     * Lấy thông tin người dùng hiện tại đang đăng nhập.
     * (Tạm thời đang mock để hỗ trợ phát triển các module khác).
     */
    UserSummaryDto getCurrentUser();

    /**
     * Lấy ID của người dùng hiện tại đang đăng nhập.
     */
    Long getCurrentUserId();
}
