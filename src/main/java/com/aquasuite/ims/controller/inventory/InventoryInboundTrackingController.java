package com.aquasuite.ims.controller.inventory;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.inventory.response.InventoryInboundKpiResponse;
import com.aquasuite.ims.dto.inventory.response.ReadableStockMovementResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundSummaryResponse;
import com.aquasuite.ims.service.inventory.InventoryInboundTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API chỉ đọc bổ trợ cho màn Quản lý Tồn kho: theo dõi lượng hàng đang chờ về từ Đơn mua (PO),
 * tiến độ Phiếu nhập kho (GRN), các thẻ KPI hành động và lịch sử thẻ kho tường minh.
 *
 * <p>Tách độc lập với {@link InventoryController} để bảo toàn nguyên vẹn hợp đồng API,
 * Sequence Diagram và Class Diagram của UC-17.1 / UC-17.2 / UC-16.1.</p>
 */
@RestController
@RequestMapping("/api/v1/inventory/inbound-tracking")
@PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
@RequiredArgsConstructor
public class InventoryInboundTrackingController {

    private final InventoryInboundTrackingService inboundTrackingService;

    /**
     * Lấy số liệu 4 thẻ KPI tổng hợp nhanh ở đầu trang Quản lý Tồn kho.
     */
    @GetMapping("/kpi-overview")
    public ResponseEntity<ApiResponse<InventoryInboundKpiResponse>> getKpiOverview() {
        InventoryInboundKpiResponse response = inboundTrackingService.getKpiOverview();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Lấy tóm tắt lượng hàng đang chờ về từ PO theo danh sách SKU đang hiển thị trên bảng tồn kho.
     */
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<Map<UUID, VariantInboundSummaryResponse>>> getInboundSummaries(
            @RequestParam(required = false) List<UUID> variantIds) {
        Map<UUID, VariantInboundSummaryResponse> response = inboundTrackingService.getInboundSummaries(variantIds);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Xem tiến độ các Đơn mua hàng (PO) và các đợt Phiếu nhập kho (GRN) của một SKU.
     */
    @GetMapping("/variants/{variantId}/purchase-orders")
    public ResponseEntity<ApiResponse<VariantInboundDetailResponse>> getVariantPurchaseOrders(
            @PathVariable UUID variantId,
            @RequestParam(required = false, defaultValue = "false") boolean includeCompleted) {
        VariantInboundDetailResponse response =
                inboundTrackingService.getVariantPurchaseOrders(variantId, includeCompleted);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Xem lịch sử thẻ kho đã làm sạch chuỗi JSON kỹ thuật và gắn liên kết chứng từ PO / GRN / Đơn bán.
     *
     * @param group nhóm lọc tùy chọn: ALL (mặc định) | INBOUND | OUTBOUND | ADJUSTMENT
     */
    @GetMapping("/variants/{variantId}/movements")
    public ResponseEntity<ApiResponse<Page<ReadableStockMovementResponse>>> getReadableMovements(
            @PathVariable UUID variantId,
            @RequestParam(required = false, defaultValue = "ALL") String group,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReadableStockMovementResponse> response =
                inboundTrackingService.getReadableMovements(variantId, group, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
