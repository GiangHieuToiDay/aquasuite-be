package com.aquasuite.ims.module.inventory.internal.payload.res;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng chi tiết của phiếu nhập kho trả về cho client (UC-32).
 *
 * @param goodsReceiptItemId  khóa dòng phiếu nhập
 * @param purchaseOrderItemId dòng đơn mua hàng tương ứng
 * @param variantId           SKU/variant đã nhập
 * @param unit                đơn vị tính
 * @param quantity            số lượng thực nhận trên dòng này
 * @param unitCost            đơn giá nhập áp dụng
 * @param lineTotal           thành tiền dòng = quantity × unitCost
 */
@Builder
public record GoodsReceiptItemResponse(
        UUID goodsReceiptItemId,
        UUID purchaseOrderItemId,
        UUID variantId,
        String unit,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal lineTotal
) {}
