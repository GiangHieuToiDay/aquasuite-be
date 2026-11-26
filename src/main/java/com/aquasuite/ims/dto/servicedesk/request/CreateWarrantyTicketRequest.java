package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.enums.servicedesk.PreferredSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        @Size(min = 10, max = 2000, message = "Mô tả sự cố phải từ 10 đến 2000 ký tự.")
        String issueDescription,

        @FutureOrPresent(message = "Ngày hẹn mong muốn không được ở trong quá khứ.")
        LocalDate preferredDate,

        PreferredSlot preferredSlot,

        @NotBlank(message = "Địa chỉ xử lý là bắt buộc.")
        @Size(max = 500, message = "Địa chỉ xử lý không được vượt quá 500 ký tự.")
        String siteAddress,

        @Size(max = 6, message = "Tối đa 6 tệp đính kèm.")
        List<@NotNull(message = "Tệp đính kèm không được rỗng.") @Valid TicketMedia> media,

        /** Khách chủ động xác nhận vẫn gửi dù thẻ bảo hành đã hết hạn (C+). Bắt buộc true nếu thẻ hết hạn. */
        boolean acknowledgeExpired
) {
}

