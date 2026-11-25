package com.aquasuite.ims.controller.inventory;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.dto.inventory.response.SkuReservationStock;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.entity.inventory.StockMovement;
import com.aquasuite.ims.dto.inventory.request.ManualStockAdjustRequest;
import com.aquasuite.ims.dto.inventory.request.UpdateInventoryThresholdRequest;
import com.aquasuite.ims.dto.inventory.response.InventoryListItemResponse;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
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
    private final StockReservationService stockReservationService;
    private final IamQueryService iamApi;

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
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật ngưỡng tồn kho an toàn thành công."));
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

    /**
     * UC36 read-only view of the units held and still available for each SKU/warehouse.
     * (from StockReservationQueryController)
     */
    @GetMapping("/stock-reservations/variants/{variantId}")
    public ApiResponse<List<SkuReservationStock>> stockReservationsByVariant(@PathVariable UUID variantId) {
        var actor = iamApi.getCurrentUser();
        if (actor == null || actor.id() == null || actor.roles() == null || actor.roles().stream()
                .noneMatch(Set.of("SHOP_OWNER", "ADMIN", "SUPER_ADMIN", "SALES_STAFF")::contains)) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return ApiResponse.ok(stockReservationService.stockByVariant(variantId));
    }
}
