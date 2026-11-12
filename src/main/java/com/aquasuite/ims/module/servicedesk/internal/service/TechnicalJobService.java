package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CompleteTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.TechnicalJobDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.TechnicalJobListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Nghiệp vụ công việc kỹ thuật (FE-13) phục vụ Giai đoạn 1 - thao tác phía Technical Staff.
 * <ul>
 *   <li>UC-59: tra cứu danh sách/lịch và xem chi tiết công việc được phân công.</li>
 *   <li>UC-60: tiếp nhận (accept) hoặc từ chối (reject) công việc.</li>
 *   <li>UC-61: ghi kết quả và hoàn tất công việc.</li>
 * </ul>
 * Kỹ thuật viên chỉ thao tác trên công việc được phân cho chính mình; Chủ cửa hàng được xem toàn bộ.
 * Việc tạo và phân công công việc nằm ngoài phạm vi (Giai đoạn 2 - auto từ Order/Bảo hành).
 */
public interface TechnicalJobService {

    /**
     * Tra cứu danh sách công việc kỹ thuật có phân trang và lọc (UC-59).
     * Phạm vi dữ liệu tự động áp theo người đăng nhập: Technical Staff chỉ thấy công việc của mình,
     * Chủ cửa hàng thấy toàn bộ.
     */
    Page<TechnicalJobListItemResponse> searchJobs(TechnicalJobStatus status,
                                                  TechnicalJobType jobType,
                                                  OffsetDateTime from,
                                                  OffsetDateTime to,
                                                  Pageable pageable);

    /** Xem chi tiết một công việc kỹ thuật (UC-59). */
    TechnicalJobDetailResponse getJobDetail(UUID technicalJobId);

    /**
     * Tạo công việc kỹ thuật thủ công (thao tác của Chủ cửa hàng).
     * Không phụ thuộc loại đơn — dùng cho mọi khách và cả 3 loại công việc. Có thể gán sẵn kỹ thuật viên.
     * Công việc khởi tạo ở trạng thái ASSIGNED; {@code createdBy} là người đang đăng nhập.
     */
    TechnicalJobDetailResponse createJob(CreateTechnicalJobRequest request);

    /**
     * Phân công công việc cho một kỹ thuật viên (thao tác của Chủ cửa hàng), đồng thời đặt/cập nhật lịch hẹn.
     * Áp dụng cho công việc đang ở ASSIGNED hoặc REJECTED (phân công lại sau khi bị từ chối);
     * khi phân công lại việc đã REJECTED sẽ đưa về ASSIGNED và xóa lý do từ chối cũ.
     *
     * @param scheduledAt lịch hẹn mới (tùy chọn); nếu null thì giữ nguyên lịch hẹn hiện có
     */
    TechnicalJobDetailResponse assignJob(UUID technicalJobId, UUID technicianId, java.time.OffsetDateTime scheduledAt);

    /** Tiếp nhận công việc: ASSIGNED → IN_PROGRESS (UC-60). */
    TechnicalJobDetailResponse acceptJob(UUID technicalJobId);

    /** Từ chối công việc: ASSIGNED → REJECTED, kèm lý do (UC-60). */
    TechnicalJobDetailResponse rejectJob(UUID technicalJobId, String reason);

    /** Ghi kết quả và hoàn tất công việc: IN_PROGRESS → COMPLETED (UC-61). */
    TechnicalJobDetailResponse completeJob(UUID technicalJobId, CompleteTechnicalJobRequest request);
}
