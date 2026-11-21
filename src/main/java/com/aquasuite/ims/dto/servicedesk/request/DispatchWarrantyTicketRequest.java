package com.aquasuite.ims.dto.servicedesk.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yêu cầu "bắn job" sửa chữa bảo hành từ một yêu cầu bảo hành (ticket) — thao tác của Chủ cửa hàng (FE-13 GĐ 2b).
 *
 * @param technicianId   kỹ thuật viên được phân công (bắt buộc; phải là tài khoản TECHNICAL_STAFF)
 * @param scheduledAt    lịch hẹn dự kiến (tùy chọn)
 * @param serviceAddress địa chỉ dịch vụ (tùy chọn; để trống thì lấy theo địa chỉ khách ghi trên ticket)
 */
public record DispatchWarrantyTicketRequest(
        @NotNull(message = "Kỹ thuật viên không được để trống")
        UUID technicianId,

        OffsetDateTime scheduledAt,

        @Size(max = 1000, message = "Địa chỉ dịch vụ tối đa 1000 ký tự")
        String serviceAddress) {
}
