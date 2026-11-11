package com.aquasuite.ims.module.inventory.internal.payload.res;

import com.aquasuite.ims.module.inventory.internal.enums.InventoryStockStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng tồn kho hiển thị trên màn tra cứu/tìm kiếm tồn kho (UC-31).
 * Gộp số liệu tồn từ module Inventory với thông tin SKU (mã, tên, đơn vị) lấy từ module Catalog.
 *
 * @param variantId   mã biến thể (SKU) – khóa tham chiếu scalar sang Catalog
 * @param sku         mã SKU hiển thị
 * @param productName tên sản phẩm (fallback sang tên biến thể nếu thiếu)
 * @param unit        đơn vị tính
 * @param onHandQty   tồn vật lý thực tế
 * @param reservedQty số lượng đang được giữ cho đơn hàng
 * @param availableQty tồn khả dụng = onHandQty - reservedQty
 * @param minimumQty  ngưỡng tồn tối thiểu (safety stock)
 * @param maximumQty  ngưỡng tồn tối đa (có thể null)
 * @param status      trạng thái tồn kho suy ra ({@link InventoryStockStatus})
 * @param lowStock    cờ cảnh báo sắp hết hàng
 */
@Builder
public record InventoryListItemResponse(
        UUID variantId,
        String sku,
        String productName,
        String unit,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        BigDecimal availableQty,
        BigDecimal minimumQty,
        BigDecimal maximumQty,
        InventoryStockStatus status,
        boolean lowStock) {}
