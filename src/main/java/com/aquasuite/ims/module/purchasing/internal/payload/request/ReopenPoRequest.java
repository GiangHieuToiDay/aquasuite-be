package com.aquasuite.ims.module.purchasing.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReopenPoRequest {

    @NotBlank(message = "Lý do mở lại đơn mua hàng không được để trống")
    private String reopenReason;
}

