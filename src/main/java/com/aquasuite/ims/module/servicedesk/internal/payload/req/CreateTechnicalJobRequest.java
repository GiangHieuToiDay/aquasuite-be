package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yêu cầu tạo công việc kỹ thuật thủ công (thao tác của Chủ cửa hàng).
 * <p>Không giới hạn theo loại đơn: dùng cho mọi khách (khách lẻ/mua tại quầy/công trình/ad-hoc) và cả 3 loại
 * công việc (Khảo sát / Lắp đặt / Bảo hành). Có thể gán luôn kỹ thuật viên khi tạo, hoặc để trống để phân công sau.</p>
 *
 * @param jobType         loại công việc (bắt buộc)
 * @param customerId      khách hàng (bắt buộc, phải tồn tại trong CRM)
 * @param serviceAddress  địa chỉ dịch vụ/lắp đặt (bắt buộc)
 * @param issueDescription mô tả/yêu cầu công việc (bắt buộc)
 * @param scheduledAt     lịch hẹn dự kiến (tùy chọn)
 * @param technicianId    gán sẵn cho kỹ thuật viên (tùy chọn; nếu có phải là tài khoản TECHNICAL_STAFF)
 * @param orderId         liên kết đơn hàng nguồn (tùy chọn)
 * @param warrantyRecordId liên kết bản ghi bảo hành (tùy chọn)
 */
public record CreateTechnicalJobRequest(
        @NotNull(message = "Loại công việc không được để trống")
        TechnicalJobType jobType,

        @NotNull(message = "Khách hàng không được để trống")
        UUID customerId,

        @NotBlank(message = "Địa chỉ dịch vụ không được để trống")
        @Size(max = 1000, message = "Địa chỉ dịch vụ tối đa 1000 ký tự")
        String serviceAddress,

        @NotBlank(message = "Mô tả công việc không được để trống")
        @Size(max = 4000, message = "Mô tả công việc tối đa 4000 ký tự")
        String issueDescription,

        OffsetDateTime scheduledAt,

        UUID technicianId,

        UUID orderId,

        UUID warrantyRecordId) {
}
