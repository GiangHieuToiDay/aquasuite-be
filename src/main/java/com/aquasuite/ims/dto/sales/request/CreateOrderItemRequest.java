package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
public class CreateOrderItemRequest {

    @NotNull(message = "variantId không được để trống")
    private UUID variantId;

    @NotNull(message = "Số lượng không được để trống")
    @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
    @Digits(integer = 10, fraction = 3, message = "Số lượng tối đa 10 chữ số nguyên và 3 chữ số thập phân")
    private BigDecimal quantity;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Giảm giá dòng không được âm")
    @Digits(integer = 13, fraction = 2, message = "Giảm giá dòng tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal discountAmount = BigDecimal.ZERO;
}
