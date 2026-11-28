package com.aquasuite.ims.service.inventory;

import com.aquasuite.ims.dto.inventory.response.InventoryInboundKpiResponse;
import com.aquasuite.ims.dto.inventory.response.ReadableStockMovementResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Dịch vụ chỉ đọc cung cấp dữ liệu điều phối Đơn mua hàng đang về (PO), Phiếu nhập kho (GRN)
 * và Thẻ kho tường minh cho màn Quản lý Tồn kho.
 * Tách độc lập với {@link InventoryService} để bảo toàn nguyên vẹn hợp đồng và sơ đồ của UC-17.1 / UC-17.2.
 */
public interface InventoryInboundTrackingService {

    /** Số liệu tổng hợp 4 thẻ KPI trên toàn kho chính. */
    InventoryInboundKpiResponse getKpiOverview();

    /** Map tóm tắt hàng đang về từ PO theo danh sách variantId trên trang hiện tại. */
    Map<UUID, VariantInboundSummaryResponse> getInboundSummaries(Collection<UUID> variantIds);

    /** Chi tiết tiến độ các đơn mua (PO) và đợt nhập (GRN) của một SKU. */
    VariantInboundDetailResponse getVariantPurchaseOrders(UUID variantId, boolean includeCompleted);

    /** Lịch sử thẻ kho đã làm sạch chuỗi JSON nội bộ và gắn liên kết chứng từ PO/GRN/Đơn bán. */
    Page<ReadableStockMovementResponse> getReadableMovements(UUID variantId, String group, Pageable pageable);
}
