package com.aquasuite.ims.dto.servicedesk.request;

/**
 * Payload khách hàng hủy yêu cầu bảo hành khi còn ở trạng thái NEW (UC-47).
 */
public record CancelWarrantyTicketRequest(
        String reason
) {
}

