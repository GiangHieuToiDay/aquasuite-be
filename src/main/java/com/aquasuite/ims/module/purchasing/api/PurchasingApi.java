package com.aquasuite.ims.module.purchasing.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Public Interface của Module Purchasing.
 * Các module khác (Inventory, Catalog, Sales, ...) tương tác với đơn mua hàng qua Interface này.
 */
public interface PurchasingApi {

    /**
     * Lấy số lượng hàng đang đặt (sắp về) cho 1 SKU từ các PO đang mở (CONFIRMED hoặc PARTIALLY_RECEIVED).
     */
    BigDecimal getIncomingStockQty(UUID variantId);

    /**
     * Lấy số lượng hàng đang đặt (sắp về) cho danh sách SKU từ các PO đang mở.
     */
    Map<UUID, BigDecimal> getIncomingStockQtyMap(Collection<UUID> variantIds);

    /**
     * Nhận thông báo xác nhận phiếu nhập hàng từ Module Inventory để cập nhật tiến độ nhận và trạng thái PO.
     */
    void onGoodsReceiptConfirmed(UUID purchaseOrderId, Map<UUID, BigDecimal> receivedItems);
}