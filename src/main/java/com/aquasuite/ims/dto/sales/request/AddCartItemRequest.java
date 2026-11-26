package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload yêu cầu thêm biến thể sản phẩm vào giỏ hàng của khách hàng (UC-17).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCartItemRequest {

    @NotNull(message = "variantId không được để trống")
    private UUID variantId;

    @NotNull(message = "Số lượng không được để trống")
    @DecimalMin(value = "1", message = "Số lượng phải từ 1 trở lên")
    @DecimalMax(value = "9999999999.99", message = "Số lượng vượt quá giới hạn cho phép")
    @Digits(integer = 10, fraction = 2, message = "Số lượng tối đa 10 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal quantity;
}

