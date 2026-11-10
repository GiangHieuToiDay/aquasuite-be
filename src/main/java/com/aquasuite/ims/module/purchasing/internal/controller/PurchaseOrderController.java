package com.aquasuite.ims.module.purchasing.internal.controller;

import com.aquasuite.ims.module.purchasing.internal.payload.request.*;
import com.aquasuite.ims.module.purchasing.internal.payload.response.LowStockSuggestionResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderListItemResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.StageRollbackRequestResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierItemOptionResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierOptionResponse;
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
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /**
     * 1. Tạo đơn mua hàng nháp (Create DRAFT PO - không nhập đơn giá, total_amount = 0)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
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
     * 3. Sửa đơn mua hàng nháp (nhập đơn giá, ngày giao dự kiến, chia đợt)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
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
     * 5. Xác nhận đơn mua hàng (QUOTED/DRAFT -> CONFIRMED, khóa danh sách hàng, yêu cầu đủ đơn giá & ngày giao các đợt)
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> confirmPurchaseOrder(@PathVariable UUID id) {
        log.info("API: Confirm purchase order: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.confirmPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xác nhận đơn mua hàng thành công"));
    }

    /**
     * 6. Mở lại đơn mua hàng để sửa (CONFIRMED -> QUOTED, chỉ khi chưa có đợt nào SETTLED)
     */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> reopenToQuoted(
            @PathVariable UUID id,
            @Valid @RequestBody ReopenPoRequest request) {
        log.info("API: Reopen PO to quoted: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.reopenToQuoted(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Mở lại đơn mua hàng để chỉnh sửa thành công"));
    }

    /**
     * 7. Xác nhận nhận hàng và thanh toán đợt giao
     */
    @PostMapping("/{id}/stages/{stageId}/settle")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> settleStage(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @Valid @RequestBody SettleStageRequest request) {
        log.info("API: Settle stage {} for PO: {}", stageId, id);
        PurchaseOrderResponse response = purchaseOrderService.settleStage(id, stageId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xác nhận nhận hàng và thanh toán đợt thành công"));
    }

    /**
     * 8. Kế toán gửi yêu cầu hoàn tác đợt thanh toán
     */
    @PostMapping("/{id}/stages/{stageId}/rollback-request")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse<StageRollbackRequestResponse>> requestStageRollback(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @Valid @RequestBody StageRollbackRequest request) {
        log.info("API: Request stage rollback for stage {} in PO: {}", stageId, id);
        StageRollbackRequestResponse response = purchaseOrderService.requestStageRollback(id, stageId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Gửi yêu cầu hoàn tác đợt thành công"));
    }

    /**
     * 9. Chủ cửa hàng phê duyệt hoặc từ chối yêu cầu hoàn tác đợt
     */
    @PostMapping("/{id}/stages/rollback-requests/{rollbackId}/review")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> reviewStageRollback(
            @PathVariable UUID id,
            @PathVariable UUID rollbackId,
            @Valid @RequestBody ReviewRollbackRequest request) {
        log.info("API: Review stage rollback {} for PO: {}", rollbackId, id);
        PurchaseOrderResponse response = purchaseOrderService.reviewStageRollback(id, rollbackId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xử lý yêu cầu hoàn tác đợt thành công"));
    }

    /**
     * 10. Chủ cửa hàng tự hoàn tác trực tiếp đợt thanh toán
     */
    @PostMapping("/{id}/stages/{stageId}/rollback-direct")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> directRollbackStage(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @Valid @RequestBody StageRollbackRequest request) {
        log.info("API: Direct rollback stage {} for PO: {}", stageId, id);
        PurchaseOrderResponse response = purchaseOrderService.directRollbackStage(id, stageId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Hoàn tác đợt thanh toán thành công"));
    }

    /**
     * 11. Đóng PO nhận thiếu (khi đã có ít nhất 1 đợt SETTLED, NCC không giao tiếp)
     */
    @PostMapping("/{id}/close-short")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closeShortPurchaseOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CloseShortPoRequest request) {
        log.info("API: Close short PO: {}", id);
        PurchaseOrderResponse response = purchaseOrderService.closeShortPurchaseOrder(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đóng đơn mua hàng nhận thiếu thành công"));
    }

    /**
     * 12. Hủy đơn mua hàng (chỉ khi chưa có đợt nào SETTLED)
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
     * 13. Đóng đơn mua hàng (cho quy trình nhập kho truyền thống nếu có)
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
     * 14. Xuất file PDF đơn mua hàng
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
     * 15. Gợi ý hàng sắp hết cho nhà cung cấp (nút Auto-Fill)
     */
    @GetMapping("/low-stock-suggestions")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<List<LowStockSuggestionResponse>>> getLowStockSuggestions(
            @RequestParam UUID supplierId) {
        log.info("API: Get low stock suggestions for supplier: {}", supplierId);
        List<LowStockSuggestionResponse> response = purchaseOrderService.getLowStockSuggestions(supplierId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách gợi ý hàng sắp hết thành công"));
    }

    /**
     * 16. Danh sách SKU của nhà cung cấp hoặc toàn bộ để thêm dòng hàng
     */
    @GetMapping("/supplier-items")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<List<SupplierItemOptionResponse>>> getSupplierItems(
            @RequestParam UUID supplierId,
            @RequestParam(defaultValue = "false") boolean all) {
        log.info("API: Get supplier items for supplier: {}, all={}", supplierId, all);
        List<SupplierItemOptionResponse> response = purchaseOrderService.getSupplierItems(supplierId, all);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách sản phẩm thành công"));
    }

    /**
     * 17. Danh sách nhà cung cấp đang hoạt động
     */
    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ACCOUNTANT', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<List<SupplierOptionResponse>>> getActiveSuppliers() {
        log.info("API: Get active suppliers list");
        List<SupplierOptionResponse> response = purchaseOrderService.getActiveSuppliers();
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách nhà cung cấp thành công"));
    }
}
