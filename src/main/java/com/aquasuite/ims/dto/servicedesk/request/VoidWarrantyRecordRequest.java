package com.aquasuite.ims.dto.servicedesk.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidWarrantyRecordRequest(
        @NotBlank(message = "Vui lòng nhập lý do hủy thẻ bảo hành.")
        @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự.")
        String reason
) {
}

