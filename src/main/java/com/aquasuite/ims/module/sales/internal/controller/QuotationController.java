package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.payload.request.ApproveQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.ConvertQuotationToOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkAcceptedRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkLostRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationUpsertRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RejectQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SubmitApprovalRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationListItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.service.QuotationService;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller điều phối các nghiệp vụ Quản lý Báo giá (Sales Quotations).
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/sales/quotations", "/api/v1/quotations"})
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;
    private final SalesOrderService salesOrderService;

    /**
     * 1. Tính nhẩm trước báo giá (Preview calculation - Pure in-memory)
     */
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<QuotationResponse>> previewCalculation(
            @Valid @RequestBody QuotationUpsertRequest request) {
        log.info("API: Preview calculation for customerId={}", request.getCustomerId());
        QuotationResponse response = quotationService.previewCalculation(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Tính nhẩm báo giá thành công"));
    }

    /**
     * 2. Tạo báo giá mới (Lưu nháp / DRAFT)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<QuotationResponse>> createQuotation(
            @Valid @RequestBody QuotationUpsertRequest request) {
        log.info("API: Create quotation for customerId={}", request.getCustomerId());
        QuotationResponse response = quotationService.createQuotation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Tạo báo giá thành công"));
    }

    /**
     * 3. Lấy thông tin chi tiết báo giá theo ID
     */
    @GetMapping("/{quotationId}")
    public ResponseEntity<ApiResponse<QuotationResponse>> getQuotationById(
            @PathVariable UUID quotationId) {
        log.info("API: Get quotation details quotationId={}", quotationId);
        QuotationResponse response = quotationService.getQuotationById(quotationId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy chi tiết báo giá thành công"));
    }

    /**
     * 4. Cập nhật nội dung báo giá nháp
     */
    @PutMapping("/{quotationId}")
    public ResponseEntity<ApiResponse<QuotationResponse>> updateQuotation(
            @PathVariable UUID quotationId,
            @Valid @RequestBody QuotationUpsertRequest request) {
        log.info("API: Update quotation quotationId={}", quotationId);
        QuotationResponse response = quotationService.updateQuotation(quotationId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Cập nhật báo giá thành công"));
    }

    /**
     * 5. Xóa bản ghi báo giá nháp
     */
    @DeleteMapping("/{quotationId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuotation(
            @PathVariable UUID quotationId) {
        log.info("API: Delete quotation quotationId={}", quotationId);
        quotationService.deleteQuotation(quotationId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Xóa báo giá nháp thành công"));
    }

    /**
     * 6. Trình phê duyệt báo giá vượt khung chiết khấu
     */
    @PostMapping("/{quotationId}/submit-approval")
    public ResponseEntity<ApiResponse<QuotationResponse>> submitForApproval(
            @PathVariable UUID quotationId,
            @Valid @RequestBody SubmitApprovalRequest request) {
        log.info("API: Submit for approval quotationId={}", quotationId);
        QuotationResponse response = quotationService.submitForApproval(quotationId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Trình phê duyệt báo giá thành công"));
    }

    /**
     * 7. Quản trị viên phê duyệt báo giá
     */
    @PostMapping("/{quotationId}/approve")
    public ResponseEntity<ApiResponse<QuotationResponse>> approveQuotation(
            @PathVariable UUID quotationId,
            @RequestBody(required = false) ApproveQuotationRequest request) {
        log.info("API: Approve quotation quotationId={}", quotationId);
        QuotationResponse response = quotationService.approveQuotation(quotationId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Phê duyệt báo giá thành công"));
    }

    /**
     * 8. Quản trị viên từ chối báo giá
     */
    @PostMapping("/{quotationId}/reject")
    public ResponseEntity<ApiResponse<QuotationResponse>> rejectQuotation(
            @PathVariable UUID quotationId,
            @Valid @RequestBody RejectQuotationRequest request) {
        log.info("API: Reject quotation quotationId={}", quotationId);
        QuotationResponse response = quotationService.rejectQuotation(quotationId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Từ chối báo giá thành công"));
    }

    /**
     * 9. Tạo phiên bản đàm phán sửa đổi mới (Revision)
     */
    @PostMapping("/{quotationId}/revisions")
    public ResponseEntity<ApiResponse<QuotationResponse>> createRevision(
            @PathVariable UUID quotationId) {
        log.info("API: Create revision from quotationId={}", quotationId);
        QuotationResponse response = quotationService.createRevision(quotationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, "Tạo phiên bản sửa đổi mới thành công"));
    }

    /**
     * 10. Chốt đơn hàng thành công (Khách hàng chấp thuận)
     */
    @PostMapping("/{quotationId}/accept")
    public ResponseEntity<ApiResponse<QuotationResponse>> markAccepted(
            @PathVariable UUID quotationId,
            @RequestBody(required = false) MarkAcceptedRequest request) {
        log.info("API: Mark quotation accepted quotationId={}", quotationId);
        QuotationResponse response = quotationService.markAccepted(quotationId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Chốt đơn báo giá thành công"));
    }

    /**
     * Phát hành / Gửi báo giá cho khách hàng (chuyển sang SENT)
     */
    @PostMapping("/{quotationId}/send")
    public ResponseEntity<ApiResponse<QuotationResponse>> sendQuotation(
            @PathVariable UUID quotationId) {
        log.info("API: Send quotation quotationId={}", quotationId);
        QuotationResponse response = quotationService.sendQuotation(quotationId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Phát hành báo giá thành công"));
    }

    /**
     * 11. Ghi nhận thất bại / Mất dự án
     */
    @PostMapping("/{quotationId}/lost")
    public ResponseEntity<ApiResponse<QuotationResponse>> markLost(
            @PathVariable UUID quotationId,
            @Valid @RequestBody MarkLostRequest request) {
        log.info("API: Mark quotation lost quotationId={}", quotationId);
        QuotationResponse response = quotationService.markLost(quotationId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Ghi nhận mất dự án thành công"));
    }

    /**
     * 12. Lấy toàn bộ lịch sử các phiên bản của một mã báo giá
     */
    @GetMapping("/code/{quotationCode}/revisions")
    public ResponseEntity<ApiResponse<List<QuotationListItemResponse>>> getRevisionHistory(
            @PathVariable String quotationCode) {
        log.info("API: Get revision history for quotationCode={}", quotationCode);
        List<QuotationListItemResponse> response = quotationService.getRevisionHistory(quotationCode);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy lịch sử phiên bản báo giá thành công"));
    }

    /**
     * 13. Tìm kiếm, lọc và phân trang danh sách báo giá
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<QuotationListItemResponse>>> listQuotations(
            QuotationFilterRequest filterRequest) {
        log.info("API: List quotations with filters");
        PageResponse<QuotationListItemResponse> response = quotationService.listQuotations(filterRequest);
        return ResponseEntity.ok(ApiResponse.ok(response, "Lấy danh sách báo giá thành công"));
    }

    /**
     * 14. Chuyển đổi báo giá đã duyệt/chốt thành đơn hàng công trình (UC-28 / UC-073)
     */
    @PostMapping("/{quotationId}/convert-to-order")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> convertToOrder(
            @PathVariable UUID quotationId,
            @RequestBody(required = false) ConvertQuotationToOrderRequest request) {
        log.info("API: Convert quotation quotationId={} to sales order", quotationId);
        SalesOrderDetailResponse response = salesOrderService.convertQuotationToOrder(quotationId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Chuyển báo giá thành đơn hàng công trình thành công!"));
    }
}
