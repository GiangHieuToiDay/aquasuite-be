package com.aquasuite.ims.dto.servicedesk.request;

import jakarta.validation.constraints.Size;

/**
 * Payload khách hàng hủy yêu cầu bảo hành khi còn ở trạng thái NEW (UC-47).
 */
public record CancelWarrantyTicketRequest(
        @Size(max = 500, message = "Lý do hủy yêu cầu tối đa 500 ký tự.")
        String reason
) {
}

