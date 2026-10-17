package com.aquasuite.ims.module.inventory;

import com.aquasuite.ims.module.inventory.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.dto.StockSummaryDto;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InventoryFacade {

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
}
