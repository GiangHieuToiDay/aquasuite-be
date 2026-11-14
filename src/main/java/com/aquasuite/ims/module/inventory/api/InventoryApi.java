package com.aquasuite.ims.module.inventory.api;

import com.aquasuite.ims.module.inventory.api.dto.GoodsReceiptSummaryDto;
import com.aquasuite.ims.module.inventory.api.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.api.dto.VariantInventoryUsage;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Interface của Module Inventory.
 * Các module khác (Sales, Fulfillment, Finance, Purchasing, ...) giao tiếp đồng bộ với Inventory qua Interface này.
 */
public interface InventoryApi {

    /** Atomic delivery stock-out, including source reservation consumption. Caller holds the order lock. */
    com.aquasuite.ims.module.inventory.api.dto.StockDispatchResult issue(
            com.aquasuite.ims.module.inventory.api.dto.StockDispatchCommand command);

    /** Lấy thông tin tồn kho của 1 SKU */
    Optional<StockSummaryDto> getStockSummary(UUID variantId);

    /** Lấy thông tin tồn kho của danh sách SKU */
    Map<UUID, StockSummaryDto> getStockSummaries(Collection<UUID> variantIds);

    /** Kiểm tra xem SKU có đủ số lượng khả dụng (availableQty) hay không */
    boolean checkAvailability(UUID variantId, BigDecimal requiredQty);

    /** Đặt giữ hàng khi tạo/xác nhận đơn hàng (tăng reservedQty) */
    void reserveStock(UUID variantId, BigDecimal qty);

    /** Hủy đặt giữ hàng khi hủy đơn hàng (giảm reservedQty) */
    void releaseReservedStock(UUID variantId, BigDecimal qty);

    /** Thay đổi số lượng tồn kho vật lý (onHandQty) và ghi thẻ kho bất biến (stock_movements) */
    void adjustPhysicalStock(StockAdjustCommand command);
    void initializeMainWarehouseInventory(UUID variantId);
    VariantInventoryUsage getVariantDeletionUsage(UUID variantId);
    VariantInventoryUsage lockAndGetVariantDeletionUsage(UUID variantId);
    void deleteEmptyVariantInventory(UUID variantId);

    /** Kiểm tra xem đơn mua hàng (PO) có phiếu nhập kho nháp (DRAFT) hay không */
    boolean hasDraftGoodsReceipt(UUID purchaseOrderId);

    /** Kiểm tra xem đơn mua hàng (PO) đã có phiếu nhập kho được xác nhận (CONFIRMED) hay chưa */
    boolean hasConfirmedGoodsReceipt(UUID purchaseOrderId);

    /** Lấy danh sách phiếu nhập kho theo purchaseOrderId */
    List<GoodsReceiptSummaryDto> getReceiptsByPurchaseOrderId(UUID purchaseOrderId);
}
