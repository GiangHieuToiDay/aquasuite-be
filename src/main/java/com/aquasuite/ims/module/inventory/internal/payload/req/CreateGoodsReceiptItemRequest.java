package com.aquasuite.ims.module.inventory.internal.payload.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng số lượng thực nhận trong yêu cầu lập phiếu nhập kho (UC-32).
 *
 * <p>Dòng được tham chiếu theo {@code purchaseOrderItemId} của đơn mua hàng. Số lượng phải lớn
 * hơn 0; ràng buộc "không vượt số còn phải nhận" được kiểm ở tầng service dưới khóa giao dịch.</p>
 *
 * @param purchaseOrderItemId khóa dòng đơn mua hàng cần nhập
 * @param quantity            số lượng thực tế được chấp nhận cho dòng này
 */
@Builder
public record CreateGoodsReceiptItemRequest(
        @NotNull(message = "purchaseOrderItemId không được để trống")
        UUID purchaseOrderItemId,

        @NotNull(message = "Số lượng nhận không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Số lượng nhận phải lớn hơn 0")
        BigDecimal quantity
) {}
