package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yêu cầu phân công một công việc kỹ thuật cho kỹ thuật viên (thao tác của Chủ cửa hàng).
 *
 * @param technicianId mã kỹ thuật viên được phân công (bắt buộc, phải có vai trò TECHNICAL_STAFF)
 * @param scheduledAt  lịch hẹn thực hiện (tùy chọn) — đặt/cập nhật luôn khi phân công; để trống nếu chốt lịch sau
 */
public record AssignTechnicalJobRequest(
        @NotNull(message = "Kỹ thuật viên được phân công không được để trống")
        UUID technicianId,

        OffsetDateTime scheduledAt) {
}
