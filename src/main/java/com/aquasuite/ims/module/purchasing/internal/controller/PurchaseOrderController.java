package com.aquasuite.ims.module.purchasing.internal.controller;

import com.aquasuite.ims.module.purchasing.internal.payload.request.CancelPurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.ClosePurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.CreatePurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.PurchaseOrderFilterRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.UpdatePurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.response.LowStockSuggestionResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderListItemResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierItemOptionResponse;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/purchasing/purchase-orders", "/api/v1/purchase-orders"})
@PreAuthorize("hasRole('SHOP_OWNER')")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /**
     * 1. Tạo đơn mua hàng nháp (Create DRAFT PO - không nhập đơn giá, total_amount = 0)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createDraft(
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        log.info("API: Create draft purchase order for supplier {}", request.getSupplierId());
        PurchaseOrderResponse response = purchaseOrderService.createDraft(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Tạo đơn mua hàng nháp thành công"));
    }

    /**
     * 2. Xem chi tiết đơn mua hàng
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getDetail(@PathVariable UUID id) {
        log.info("API: Get purchase order detail: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.getDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy chi tiết đơn mua hàng thành công"));
    }

    /**
     * 3. Sửa đơn mua hàng nháp (nhập đơn giá, ngày giao dự kiến, điều khoản note)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePurchaseOrderRequest request) {
        log.info("API: Update draft purchase order: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.updateDraft(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Cập nhật đơn mua hàng nháp thành công"));
    }

    /**
     * 4. Danh sách và bộ lọc đơn mua hàng
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderListItemResponse>>> listPurchaseOrders(
            @Valid PurchaseOrderFilterRequest filter) {
        log.info("API: List purchase orders with filter: {}", filter);
        PageResponse<PurchaseOrderListItemResponse> response = purchaseOrderService.listPurchaseOrders(filter);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách đơn mua hàng thành công"));
    }

    /**
     * 5. Xác nhận đơn mua hàng (DRAFT -> CONFIRMED, khóa danh sách hàng, yêu cầu đủ đơn giá)
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> confirmPurchaseOrder(@PathVariable UUID id) {
        log.info("API: Confirm purchase order: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.confirmPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xác nhận đơn mua hàng thành công"));
    }

    /**
     * 6. Hủy đơn mua hàng (DRAFT hoặc CONFIRMED khi chưa có phiếu nhập)
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelPurchaseOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelPurchaseOrderRequest request) {
        log.info("API: Cancel purchase order: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.cancelPurchaseOrder(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Hủy đơn mua hàng thành công"));
    }

    /**
     * 7. Đóng đơn mua hàng nhận thiếu (PARTIALLY_RECEIVED -> COMPLETED khi NCC không giao tiếp)
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closePurchaseOrder(
            @PathVariable UUID id,
            @Valid @RequestBody ClosePurchaseOrderRequest request) {
        log.info("API: Close purchase order: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.closePurchaseOrder(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đóng đơn mua hàng thành công"));
    }

    /**
     * 8. Xuất file PDF đơn mua hàng
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        log.info("API: Export purchase order PDF: {}", id);
        byte[] pdfBytes = purchaseOrderService.exportPdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "PO-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    /**
     * 9. Gợi ý hàng sắp hết cho nhà cung cấp (nút Auto-Fill)
     */
    @GetMapping("/low-stock-suggestions")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<List<LowStockSuggestionResponse>>> getLowStockSuggestions(
            @RequestParam UUID supplierId) {
        log.info("API: Get low stock suggestions for supplier: {}", supplierId);
        List<LowStockSuggestionResponse> response = purchaseOrderService.getLowStockSuggestions(supplierId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách gợi ý hàng sắp hết thành công"));
    }

    /**
     * 10. Danh sách SKU của nhà cung cấp hoặc toàn bộ để thêm dòng hàng
     */
    @GetMapping("/supplier-items")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT','SALES_STAFF')")
    public ResponseEntity<ApiResponse<List<SupplierItemOptionResponse>>> getSupplierItems(
            @RequestParam UUID supplierId,
            @RequestParam(defaultValue = "false") boolean all) {
        log.info("API: Get supplier items for supplier: {}, all={}", supplierId, all);
        List<SupplierItemOptionResponse> response = purchaseOrderService.getSupplierItems(supplierId, all);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách sản phẩm thành công"));
    }

    /**
     * 11. Danh sách nhà cung cấp đang hoạt động
     */
    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<List<com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierOptionResponse>>> getActiveSuppliers() {
        log.info("API: Get active suppliers list");
        List<com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierOptionResponse> response = purchaseOrderService.getActiveSuppliers();
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách nhà cung cấp thành công"));
    }
}
