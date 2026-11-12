package com.aquasuite.ims.module.inventory.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.payload.req.ManualStockAdjustRequest;
import com.aquasuite.ims.module.inventory.internal.payload.req.UpdateInventoryThresholdRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.InventoryListItemResponse;
import com.aquasuite.ims.module.inventory.internal.service.InventoryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API Tra cứu &amp; Quản lý Tồn kho (UC-31).
 * <ul>
 *   <li>Tra cứu (danh sách/tìm kiếm, chi tiết, thẻ kho, tồn thấp): Chủ cửa hàng &amp; Kế toán.</li>
 *   <li>Cấu hình ngưỡng tồn an toàn và điều chỉnh tồn thủ công: chỉ Chủ cửa hàng.</li>
 * </ul>
 * UC-31 chỉ đọc tồn và cấu hình ngưỡng; không làm thay đổi tồn vật lý (thuộc nhập kho/xuất kho/điều chỉnh/kiểm kê).
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final IamApi iamApi;

    /**
     * Tra cứu danh sách tồn kho có phân trang và lọc.
     *
     * @param keyword      từ khóa theo mã SKU hoặc tên sản phẩm (tùy chọn)
     * @param status       lọc theo trạng thái IN_STOCK | LOW_STOCK | OUT_OF_STOCK (tùy chọn)
     * @param lowStockOnly chỉ lấy SKU đang ở mức tồn thấp (mặc định false)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Page<InventoryListItemResponse>>> searchInventory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean lowStockOnly,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InventoryListItemResponse> page = inventoryService.searchInventory(keyword, status, lowStockOnly, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    /**
     * Danh sách các SKU đang ở mức tồn thấp trên toàn kho (phục vụ giám sát và cảnh báo).
     */
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Page<InventoryListItemResponse>>> getLowStock(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InventoryListItemResponse> page = inventoryService.getLowStockItems(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    /**
     * Xem thông tin tồn kho chi tiết của một biến thể (SKU).
     */
    @GetMapping("/{variantId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StockSummaryDto>> getStockSummary(@PathVariable UUID variantId) {
        StockSummaryDto summary = inventoryService.getInventoryByVariantId(variantId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Xem lịch sử biến động thẻ kho (stock movements) của một biến thể.
     */
    @GetMapping("/{variantId}/movements")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Page<StockMovement>>> getStockMovements(
            @PathVariable UUID variantId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockMovement> movements = inventoryService.getMovementsByVariantId(variantId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(movements));
    }

    /**
     * Cấu hình ngưỡng tồn an toàn (tối thiểu/tối đa) của một SKU. Chỉ Chủ cửa hàng được thực hiện.
     */
    @PatchMapping("/{variantId}/threshold")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<StockSummaryDto>> updateThreshold(
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateInventoryThresholdRequest request) {
        UUID actorUserId = iamApi.getCurrentUserId();
        StockSummaryDto updated = inventoryService.updateThreshold(
                variantId, request.minimumQty(), request.maximumQty(), actorUserId);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Inventory safety threshold updated successfully."));
    }

    /**
     * Điều chỉnh tồn kho thủ công có lý do (Manual adjustment). Chỉ Chủ cửa hàng được thực hiện.
     */
    @PostMapping("/adjust")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Void>> manualAdjustStock(@Valid @RequestBody ManualStockAdjustRequest request) {
        UUID actorUserId = iamApi.getCurrentUserId();
        inventoryService.manualAdjustStock(request.variantId(), request.deltaQty(), request.reason(), actorUserId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Điều chỉnh tồn kho thành công"));
    }
}
