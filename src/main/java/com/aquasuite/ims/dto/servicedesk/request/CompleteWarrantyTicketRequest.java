package com.aquasuite.ims.dto.servicedesk.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Chủ cửa hàng hoàn tất yêu cầu bảo hành (IN_PROGRESS → DONE).
 *
 * @param result kết quả xử lý, ví dụ "Đã thay van, miễn phí" hoặc "Không bảo hành vì lỗi do va đập" (bắt buộc)
 */
public record CompleteWarrantyTicketRequest(
        @NotBlank(message = "Kết quả xử lý không được để trống")
        @Size(max = 4000, message = "Kết quả xử lý tối đa 4000 ký tự")
        String result) {
}
