package com.aquasuite.ims.module.inventory.internal.service;

import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.payload.res.InventoryListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Nghiệp vụ nội bộ của module Inventory phục vụ UC-31 (Tra cứu &amp; Quản lý Tồn kho).
 * Chỉ đọc tồn và cấu hình ngưỡng; không thay đổi tồn vật lý (việc tăng/giảm tồn thuộc nhập kho/xuất kho/điều chỉnh).
 */
public interface InventoryService {

    /** Lấy tồn kho chi tiết của một SKU, ném lỗi nếu không tồn tại. */
    StockSummaryDto getInventoryByVariantId(UUID variantId);

    /** Lấy lịch sử thẻ kho (biến động tồn) của một SKU, có phân trang. */
    Page<StockMovement> getMovementsByVariantId(UUID variantId, Pageable pageable);

    /**
     * Tra cứu danh sách tồn kho có phân trang, kèm lọc.
     *
     * @param keyword      từ khóa tìm theo mã SKU hoặc tên sản phẩm (có thể null/blank)
     * @param status       lọc theo trạng thái 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' (có thể null)
     * @param lowStockOnly chỉ lấy các SKU đang ở mức tồn thấp
     */
    Page<InventoryListItemResponse> searchInventory(String keyword, String status, boolean lowStockOnly, Pageable pageable);

    /** Danh sách các SKU đang ở mức tồn thấp trên toàn kho (phục vụ cảnh báo), có phân trang. */
    Page<InventoryListItemResponse> getLowStockItems(Pageable pageable);

    /**
     * Cấu hình ngưỡng tồn an toàn (min/max) cho một SKU và phát sự kiện audit.
     *
     * @param minimumQty  ngưỡng tối thiểu, bắt buộc &ge; 0
     * @param maximumQty  ngưỡng tối đa, cho phép null; nếu có thì phải &ge; minimumQty
     * @param actorUserId người thực hiện (để ghi audit)
     * @return tồn kho sau khi cập nhật ngưỡng
     */
    StockSummaryDto updateThreshold(UUID variantId, BigDecimal minimumQty, BigDecimal maximumQty, UUID actorUserId);

    void manualAdjustStock(UUID variantId, BigDecimal deltaQty, String reason, UUID actorUserId);
}
