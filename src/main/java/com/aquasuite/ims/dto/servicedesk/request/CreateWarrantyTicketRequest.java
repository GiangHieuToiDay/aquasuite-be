package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.enums.servicedesk.PreferredSlot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload khách hàng gửi yêu cầu bảo hành (UC-47).
 */
@Builder
public record CreateWarrantyTicketRequest(
        @NotNull(message = "Thiếu định danh thẻ bảo hành.")
        UUID warrantyRecordId,

        @NotBlank(message = "Mô tả sự cố là bắt buộc.")
        String issueDescription,

        LocalDate preferredDate,

        PreferredSlot preferredSlot,

        @NotBlank(message = "Địa chỉ xử lý là bắt buộc.")
        String siteAddress,

        List<TicketMedia> media,

        /** Khách chủ động xác nhận vẫn gửi dù thẻ bảo hành đã hết hạn (C+). Bắt buộc true nếu thẻ hết hạn. */
        boolean acknowledgeExpired
) {
}

