package com.aquasuite.ims.service.finance;

import com.aquasuite.ims.dto.finance.command.CreditAuthorizationCommand;
import com.aquasuite.ims.dto.finance.response.CreditAuthorizationDecision;
import java.util.UUID;

/** Hợp đồng công khai để Sales kiểm tra khách và điều kiện mua nợ khi tạo đơn. */
public interface CreditControlService {
    /**
     * Khóa kiểm tra theo khách trong transaction của bên gọi và từ chối nếu khách bị BLOCKED.
     * Khóa được giữ đến khi transaction tạo đơn kết thúc.
     * @param customerId mã khách cần kiểm tra
     * @throws com.aquasuite.ims.exception.AppException nếu khách bị chặn hoặc không tồn tại
     */
    void requireCustomerOrderAllowed(UUID customerId);

    /**
     * Kiểm tra lần cuối kênh bán, kỳ hạn, công nợ và hạn mức đối với giá trị đơn mới.
     * @param command khách hàng, kênh bán và giá trị đơn đề nghị kiểm tra
     * @return quyết định cho phép, lý do và hạn mức còn khả dụng
     */
    CreditAuthorizationDecision authorize(CreditAuthorizationCommand command);
}
