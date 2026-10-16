package com.aquasuite.ims.module.inventory.internal.controller;

import com.aquasuite.ims.module.iam.IamFacade;
import com.aquasuite.ims.module.inventory.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.payload.req.ManualStockAdjustRequest;
import com.aquasuite.ims.module.inventory.internal.service.InventoryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final IamFacade iamFacade;

    /**
     * Xem thông tin tồn kho chi tiết của một biến thể (SKU)
     */
    @GetMapping("/{variantId}")
    public ResponseEntity<ApiResponse<StockSummaryDto>> getStockSummary(@PathVariable UUID variantId) {
        StockSummaryDto summary = inventoryService.getInventoryByVariantId(variantId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    /**
     * Xem lịch sử biến động thẻ kho (stock movements) của một biến thể
     */
    @GetMapping("/{variantId}/movements")
    public ResponseEntity<ApiResponse<Page<StockMovement>>> getStockMovements(
            @PathVariable UUID variantId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockMovement> movements = inventoryService.getMovementsByVariantId(variantId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(movements));
    }

    /**
     * Điều chỉnh tồn kho thủ công có lý do (Manual adjustment)
     */
    @PostMapping("/adjust")
    public ResponseEntity<ApiResponse<Void>> manualAdjustStock(@Valid @RequestBody ManualStockAdjustRequest request) {
        Long actorUserId = iamFacade.getCurrentUserId();
        inventoryService.manualAdjustStock(request.variantId(), request.deltaQty(), request.reason(), actorUserId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Điều chỉnh tồn kho thành công"));
    }
}
