package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateWarrantyFromOrderRequest(
        @NotNull(message = "Vui lòng chọn dòng sản phẩm trong đơn hàng.")
        UUID orderItemId,

        @Size(max = 255, message = "Số serial không được vượt quá 255 ký tự.")
        String serialNumber,

        @NotNull(message = "Vui lòng chọn ngày kích hoạt bảo hành.")
        LocalDate startDate
) {
}

