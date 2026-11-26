package com.aquasuite.ims.dto.inventory.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
        @Digits(integer = 10, fraction = 2, message = "Số lượng nhận tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        BigDecimal quantity
) {}
