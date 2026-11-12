package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu ghi kết quả và hoàn tất một công việc kỹ thuật (UC-61).
 *
 * @param result        kết quả thực hiện / kết quả kiểm tra hoàn tất (bắt buộc)
 * @param technicalNote ghi chú kỹ thuật bổ sung (tùy chọn)
 */
public record CompleteTechnicalJobRequest(
        @NotBlank(message = "Kết quả thực hiện không được để trống")
        @Size(max = 4000, message = "Kết quả thực hiện tối đa 4000 ký tự")
        String result,

        @Size(max = 4000, message = "Ghi chú kỹ thuật tối đa 4000 ký tự")
        String technicalNote) {
}
