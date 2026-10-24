package com.aquasuite.ims.module.inventory.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.internal.payload.req.CreateStockCountRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.StockCountResponse;
import com.aquasuite.ims.module.inventory.internal.service.StockCountService;
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
@RequestMapping("/api/v1/inventory/stock-counts")
@RequiredArgsConstructor
public class StockCountController {

    private final StockCountService stockCountService;
    private final IamApi iamApi;

    /**
     * Tạo phiếu kiểm kê kho dạng nháp (DRAFT)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockCountResponse>> createDraftStockCount(
            @Valid @RequestBody CreateStockCountRequest request) {
        UUID actorUserId = iamApi.getCurrentUserId();
        StockCountResponse response = stockCountService.createDraftStockCount(request, actorUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tạo phiếu kiểm kho thành công"));
    }

    /**
     * Duyệt phiếu kiểm kê và tự động cân bằng tồn kho
     */
    @PostMapping("/{stockCountId}/approve")
    public ResponseEntity<ApiResponse<StockCountResponse>> approveStockCount(
            @PathVariable UUID stockCountId) {
        UUID approverUserId = iamApi.getCurrentUserId();
        StockCountResponse response = stockCountService.approveStockCount(stockCountId, approverUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Duyệt phiếu kiểm kê và cân bằng kho thành công"));
    }

    /**
     * Hủy phiếu kiểm kê
     */
    @PostMapping("/{stockCountId}/cancel")
    public ResponseEntity<ApiResponse<StockCountResponse>> cancelStockCount(
            @PathVariable UUID stockCountId) {
        StockCountResponse response = stockCountService.cancelStockCount(stockCountId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Hủy phiếu kiểm kê thành công"));
    }

    /**
     * Xem chi tiết phiếu kiểm kê kèm danh sách từng SKU
     */
    @GetMapping("/{stockCountId}")
    public ResponseEntity<ApiResponse<StockCountResponse>> getStockCountDetail(
            @PathVariable UUID stockCountId) {
        StockCountResponse response = stockCountService.getStockCountDetail(stockCountId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Danh sách phiếu kiểm kê phân trang
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockCountResponse>>> listStockCounts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StockCountResponse> page = stockCountService.listStockCounts(pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
