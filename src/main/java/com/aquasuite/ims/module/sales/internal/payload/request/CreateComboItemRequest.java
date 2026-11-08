package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateComboItemRequest {

    @NotNull(message = "comboId không được để trống")
    private UUID comboId;

    @Builder.Default
    @Min(value = 1, message = "Số lượng combo phải từ 1 trở lên")
    private int quantity = 1;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
}
