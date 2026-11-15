package com.aquasuite.ims.module.servicedesk.internal.payload.req;

/**
 * Payload khách hàng hủy yêu cầu bảo hành khi còn ở trạng thái NEW (UC-47).
 */
public record CancelWarrantyTicketRequest(
        String reason
) {
}

