package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
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
    @Max(value = 10000, message = "Số lượng combo tối đa 10000")
    private int quantity = 1;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Giảm giá combo không được âm")
    @Digits(integer = 13, fraction = 2, message = "Giảm giá combo tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal discountAmount = BigDecimal.ZERO;
}
