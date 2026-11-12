package com.aquasuite.ims.module.inventory.internal.payload.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Yêu cầu cấu hình ngưỡng tồn an toàn cho một SKU (UC-31).
 * Ràng buộc "tối đa ≥ tối thiểu" được kiểm tra ở tầng service vì phụ thuộc đồng thời hai trường.
 *
 * @param minimumQty ngưỡng tồn tối thiểu (safety stock), bắt buộc và ≥ 0
 * @param maximumQty ngưỡng tồn tối đa, cho phép null (bỏ cấu hình trần)
 */
@Builder
public record UpdateInventoryThresholdRequest(
        @NotNull(message = "minimumQty không được để trống")
        @DecimalMin(value = "0", message = "minimumQty phải ≥ 0")
        @Digits(integer = 10, fraction = 2, message = "minimumQty tối đa 2 chữ số thập phân")
        BigDecimal minimumQty,

        @DecimalMin(value = "0", message = "maximumQty phải ≥ 0")
        @Digits(integer = 10, fraction = 2, message = "maximumQty tối đa 2 chữ số thập phân")
        BigDecimal maximumQty) {}
