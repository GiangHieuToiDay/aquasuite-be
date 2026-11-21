package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.DecimalMin;
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
    private BigDecimal quantity;
}

