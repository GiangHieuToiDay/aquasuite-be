package com.aquasuite.ims.module.inventory.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.internal.payload.req.CreateGoodsReceiptRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.GoodsReceiptResponse;
import com.aquasuite.ims.module.inventory.internal.service.GoodsReceiptService;
import com.aquasuite.ims.module.purchasing.api.PurchasingApi;
import com.aquasuite.ims.module.purchasing.api.dto.ReceivablePurchaseOrderView;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API Quản lý nhập kho theo đơn mua hàng (UC-32).
 *
 * <p>Quyền thao tác: Shop Owner và Accountant (đã chốt). Theo Hướng B, lập phiếu và xác nhận là
 * một endpoint duy nhất; phiếu sau xác nhận là bất biến nên không có endpoint sửa/hủy.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/goods-receipts")
@PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;
    private final PurchasingApi purchasingApi;
    private final IamApi iamApi;

    /**
     * Lấy snapshot đơn mua hàng để lập phiếu: trạng thái, kho nhận và các dòng kèm số còn phải nhận.
     */
    @GetMapping("/receivable/{purchaseOrderId}")
    public ResponseEntity<ApiResponse<ReceivablePurchaseOrderView>> getReceivablePurchaseOrder(
            @PathVariable UUID purchaseOrderId) {
        ReceivablePurchaseOrderView view = purchasingApi.getReceivablePurchaseOrder(purchaseOrderId)
                .orElseThrow(() -> new com.aquasuite.ims.share.exception.AppException(
                        com.aquasuite.ims.module.inventory.internal.error.InventoryErrorCode.GOODS_RECEIPT_PO_NOT_FOUND,
                        purchaseOrderId));
        return ResponseEntity.ok(ApiResponse.ok(view, "Lấy thông tin đơn mua hàng để nhập kho thành công"));
    }

    /**
     * Lập và xác nhận phiếu nhập kho (Hướng B: tạo = xác nhận, cộng tồn ngay).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> createAndConfirm(
            @Valid @RequestBody CreateGoodsReceiptRequest request) {
        UUID actorUserId = iamApi.getCurrentUserId();
        log.info("API: Create & confirm goods receipt for PO {}", request.purchaseOrderId());
        GoodsReceiptResponse response = goodsReceiptService.createAndConfirm(request, actorUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Lập và xác nhận phiếu nhập kho thành công"));
    }

    /**
     * Xem chi tiết một phiếu nhập kho kèm danh sách dòng.
     */
    @GetMapping("/{goodsReceiptId}")
    public ResponseEntity<ApiResponse<GoodsReceiptResponse>> getDetail(@PathVariable UUID goodsReceiptId) {
        GoodsReceiptResponse response = goodsReceiptService.getDetail(goodsReceiptId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Danh sách phiếu nhập kho của một đơn mua hàng (mới nhất trước).
     */
    @GetMapping("/by-purchase-order/{purchaseOrderId}")
    public ResponseEntity<ApiResponse<List<GoodsReceiptResponse>>> listByPurchaseOrder(
            @PathVariable UUID purchaseOrderId) {
        List<GoodsReceiptResponse> response = goodsReceiptService.listByPurchaseOrder(purchaseOrderId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Danh sách phiếu nhập kho phân trang (mới nhất trước).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<GoodsReceiptResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GoodsReceiptResponse> response = goodsReceiptService.list(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
