package com.aquasuite.ims.module.inventory.internal.enums;

/**
 * Trạng thái tồn kho hiển thị cho màn tra cứu (UC-31).
 * Suy ra từ tồn khả dụng và ngưỡng tối thiểu của SKU:
 * <ul>
 *   <li>{@link #OUT_OF_STOCK}: tồn khả dụng &le; 0.</li>
 *   <li>{@link #LOW_STOCK}: có cài ngưỡng ({@code minimumQty > 0}) và tồn khả dụng &le; ngưỡng nhưng vẫn &gt; 0.</li>
 *   <li>{@link #IN_STOCK}: các trường hợp còn lại (còn hàng và chưa chạm ngưỡng).</li>
 * </ul>
 */
public enum InventoryStockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}
