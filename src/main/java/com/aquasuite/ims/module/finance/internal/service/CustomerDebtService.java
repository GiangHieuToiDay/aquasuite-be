package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtCallResultRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtReminderRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.ReasonRequest;
import com.aquasuite.ims.module.finance.internal.pdf.StatementDocument;
import com.aquasuite.ims.module.finance.internal.payload.response.*;

import java.util.UUID;

/** Các thao tác kiểm tra tín dụng, theo dõi công nợ và xử lý thông báo cho khách hàng. */
public interface CustomerDebtService {
    /**
     * Khóa theo khách và chặn tạo đơn nếu khách đang ở trạng thái BLOCKED.
     * @param customerId mã khách cần kiểm tra
     */
    void requireCustomerOrderAllowed(UUID customerId);
    /** Tìm danh sách công nợ theo điều kiện lọc và phân trang. */
    CustomerDebtPageResponse list(String keyword, String customerType, String debtStatus, int page, int size);

    /** Tính thống kê tổng quan công nợ. */
    DebtDashboardStatsResponse stats();

    /** Xem đơn, hóa đơn và khoản thu trong chi tiết công nợ của một khách. */
    CustomerDebtDetailResponse detail(UUID customerId);

    /** Xem tình trạng chặn tín dụng của khách. */
    BlacklistStatusResponse blacklistStatus(UUID customerId);

    /**
     * Kiểm tra kênh bán, kỳ hạn, công nợ và hạn mức cho giá trị đơn dự kiến.
     * @param customerId mã khách cần kiểm tra
     * @param request kênh bán và số tiền đơn dự kiến
     * @return quyết định và thông tin tín dụng của khách
     */
    CreditCheckResponse checkCredit(UUID customerId, CreditCheckRequest request);

    /** Ghi nhận phê duyệt ngoại lệ tín dụng cho một đơn theo điều kiện nghiệp vụ. */
    void approveOverride(UUID orderId, ReasonRequest request, UUID actorId);

    /** Gửi hoặc ghi nhận yêu cầu nhắc công nợ. */
    DebtReminderResponse sendReminder(UUID customerId, DebtReminderRequest request, UUID actorId);

    /** Ghi nhận kết quả cuộc gọi nhắc công nợ. */
    void saveCallResult(UUID customerId, DebtCallResultRequest request, UUID actorId);

    /** Tạo bản sao kê công nợ nội bộ. */
    StatementDocument exportStatement(UUID customerId, UUID actorId);

    /** Gửi sao kê công nợ qua email và trả kết quả thực hiện. */
    StatementEmailResponse emailStatement(UUID customerId, UUID actorId);

    /** Chặn khách tạo đơn và ghi nhận lý do. */
    void block(UUID customerId, ReasonRequest request, UUID actorId);

    /** Khôi phục trạng thái khách và ghi nhận lý do. */
    void restore(UUID customerId, ReasonRequest request, UUID actorId);
}
