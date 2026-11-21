package com.aquasuite.ims.dto.servicedesk.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateAdHocWarrantyRequest(
        @NotNull(message = "Vui lòng chọn khách hàng.")
        UUID customerId,

        @NotNull(message = "Vui lòng chọn sản phẩm (SKU).")
        UUID variantId,

        UUID warrantyPolicyId,

        @Size(max = 255, message = "Số serial không được vượt quá 255 ký tự.")
        String serialNumber,

        @NotNull(message = "Vui lòng chọn ngày kích hoạt bảo hành.")
        LocalDate startDate
) {
}

