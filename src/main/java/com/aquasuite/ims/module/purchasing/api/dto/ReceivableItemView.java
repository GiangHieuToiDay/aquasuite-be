package com.aquasuite.ims.module.purchasing.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot công khai của một dòng đơn mua hàng phục vụ nghiệp vụ nhập kho (UC-32).
 *
 * <p>DTO này thuộc public contract của module Purchasing: module Inventory dùng nó để
 * biết dòng PO nào còn được nhận, số còn lại là bao nhiêu và đơn giá/đơn vị để lập phiếu,
 * mà không cần truy cập entity internal của Purchasing.</p>
 *
 * @param purchaseOrderItemId khóa dòng đơn mua (dùng làm tham chiếu khi lập phiếu nhập)
 * @param variantId           SKU/variant của dòng hàng
 * @param sku                 mã SKU hiển thị (có thể null)
 * @param productName         tên sản phẩm hiển thị (có thể null)
 * @param unit                đơn vị tính của dòng hàng
 * @param unitCost            đơn giá nhập đã duyệt trên PO (có thể null nếu chưa nhập)
 * @param quantityOrdered     số lượng đặt
 * @param quantityReceived    số lượng đã nhận cộng dồn tới hiện tại
 * @param remainingQty        số còn phải nhận = max(ordered - received, 0)
 */
public record ReceivableItemView(
        UUID purchaseOrderItemId,
        UUID variantId,
        String sku,
        String productName,
        String unit,
        BigDecimal unitCost,
        BigDecimal quantityOrdered,
        BigDecimal quantityReceived,
        BigDecimal remainingQty
) {}
