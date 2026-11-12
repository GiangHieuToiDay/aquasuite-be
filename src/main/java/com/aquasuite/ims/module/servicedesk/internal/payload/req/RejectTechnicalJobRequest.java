package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu từ chối nhận một công việc kỹ thuật (UC-60).
 *
 * @param reason lý do từ chối (bắt buộc)
 */
public record RejectTechnicalJobRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 2000, message = "Lý do từ chối tối đa 2000 ký tự")
        String reason) {
}
