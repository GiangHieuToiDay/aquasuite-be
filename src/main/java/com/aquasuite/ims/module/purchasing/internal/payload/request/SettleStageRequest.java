package com.aquasuite.ims.module.purchasing.internal.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettleStageRequest {

    @NotNull(message = "Số tiền thực chi không được để trống")
    @DecimalMin(value = "0.00", message = "Số tiền thực chi phải lớn hơn hoặc bằng 0")
    private BigDecimal actualAmount;

    private OffsetDateTime paidAt;

    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String paymentMethod;

    private String paymentReference;

    private String note;
}

