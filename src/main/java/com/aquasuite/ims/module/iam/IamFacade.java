package com.aquasuite.ims.module.iam;

import com.aquasuite.ims.module.iam.dto.UserSummaryDto;

/**
 * Public Interface của Module IAM.
 * Các module khác (Sales, CRM, Audit, ...) chỉ được phép gọi vào IAM thông qua Facade này.
 */
public interface IamFacade {

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
