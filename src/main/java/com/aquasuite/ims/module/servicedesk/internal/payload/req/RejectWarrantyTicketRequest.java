package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu Chủ cửa hàng từ chối một yêu cầu bảo hành tại bàn (FE-13 Giai đoạn 2b).
 *
 * @param reason lý do từ chối (bắt buộc) — ví dụ: thẻ hết hạn, yêu cầu trùng/rác, ngoài phạm vi bảo hành.
 */
public record RejectWarrantyTicketRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 2000, message = "Lý do từ chối tối đa 2000 ký tự")
        String reason) {
}
