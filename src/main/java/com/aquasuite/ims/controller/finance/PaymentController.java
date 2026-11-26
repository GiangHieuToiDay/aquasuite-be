package com.aquasuite.ims.controller.finance;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.finance.request.*;
import com.aquasuite.ims.dto.finance.response.*;
import com.aquasuite.ims.service.finance.PaymentAllocationService;
import com.aquasuite.ims.service.finance.PaymentService;
import com.aquasuite.ims.service.finance.impl.PaymentServiceImpl;
import com.aquasuite.ims.service.iam.IamQueryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Controller Xử lý Thu tiền, Xác minh Chuyển khoản và Phân bổ Thu chi.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentAllocationService allocationService;
    private final IamQueryService iamApi;

    @Autowired
    public PaymentController(
            PaymentService paymentService,
            PaymentAllocationService allocationService,
            IamQueryService iamApi) {
        this.paymentService = paymentService;
        this.allocationService = allocationService;
        this.iamApi = iamApi;
    }

    // ── Order Payment endpoints (/api/v1/finance/orders/{orderId}) ───────────

    @PostMapping("/api/v1/finance/orders/{orderId}/payments")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderPaymentResponse> record(
            @PathVariable UUID orderId,
            @Valid @RequestBody RecordOrderPaymentRequest request) {
        return ApiResponse.ok(paymentService.record(orderId, request));
    }

    @GetMapping("/api/v1/finance/orders/{orderId}/payments")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ApiResponse<List<OrderPaymentResponse>> history(@PathVariable UUID orderId) {
        return ApiResponse.ok(paymentService.history(orderId));
    }

    @GetMapping("/api/v1/finance/orders/{orderId}/payment-summary")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ApiResponse<OrderPaymentSummary> summary(@PathVariable UUID orderId) {
        return ApiResponse.ok(paymentService.summary(orderId));
    }

    @GetMapping("/api/v1/finance/orders/{orderId}/payment-milestones")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ApiResponse<OrderMilestoneProgressResponse> milestoneProgress(@PathVariable UUID orderId) {
        return ApiResponse.ok(paymentService.milestoneProgress(orderId));
    }

    @PostMapping(value = "/api/v1/finance/orders/{orderId}/payments/{paymentId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Void> uploadProof(
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId,
            @RequestPart("file") MultipartFile file) throws IOException {
        paymentService.upload(orderId, paymentId, file);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/v1/finance/orders/{orderId}/payments/{paymentId}/proof")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ResponseEntity<byte[]> readProof(@PathVariable UUID orderId, @PathVariable UUID paymentId) {
        var proof = paymentService.read(orderId, paymentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(proof.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payment-proof\"")
                .header("X-Content-Type-Options", "nosniff").body(proof.content());
    }

    // ── Payment Verification endpoints (/api/v1/finance/payment-verifications) 

    @GetMapping("/api/v1/finance/payment-verifications")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentVerificationPageResponse> list(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || (status != null && status.length() > 32)) {
            throw new com.aquasuite.ims.exception.AppException(
                    com.aquasuite.ims.exception.CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        return ApiResponse.ok(paymentService.list(orderId, status, page, size));
    }

    @GetMapping("/api/v1/finance/payment-verifications/{paymentId}")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentVerificationResponse> detail(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.detail(paymentId));
    }

    @PostMapping("/api/v1/finance/payment-verifications/{paymentId}/verify")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentVerificationResponse> verify(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.verify(paymentId));
    }

    @PostMapping("/api/v1/finance/payment-verifications/{paymentId}/reject")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentVerificationResponse> reject(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RejectPaymentRequest request) {
        return ApiResponse.ok(paymentService.reject(paymentId, request.reason()));
    }

    // ── Payment Allocation endpoints (/api/v1/finance/receipts) ─────────────

    @GetMapping("/api/v1/finance/receipts/unallocated")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<Page<PaymentAllocationReceiptResponse>> receipts(
            @RequestParam(defaultValue = "UNALLOCATED") PaymentAllocationReceiptView view,
            Pageable pageable) {
        return ApiResponse.ok(allocationService.listReceipts(view, pageable));
    }

    @GetMapping("/api/v1/finance/receipts/{receiptId}/allocation-targets")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentAllocationTargetsResponse> targets(@PathVariable UUID receiptId) {
        return ApiResponse.ok(allocationService.getTargets(receiptId));
    }

    @GetMapping("/api/v1/finance/receipts/{receiptId}/allocations")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<List<PaymentAllocationLineResponse>> allocations(@PathVariable UUID receiptId) {
        return ApiResponse.ok(allocationService.getAllocations(receiptId));
    }

    @PostMapping("/api/v1/finance/receipts/{receiptId}/allocations")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentAllocationBatchResponse> allocate(
            @PathVariable UUID receiptId,
            @Valid @RequestBody CreatePaymentAllocationRequest request) {
        return ApiResponse.ok(allocationService.allocate(receiptId, request, iamApi.getCurrentUserId()),
                "Payment allocated to electronically integrated invoice(s) successfully.");
    }

    @PostMapping("/api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<PaymentAllocationLineResponse> reverse(
            @PathVariable UUID receiptId,
            @PathVariable UUID allocationId,
            @Valid @RequestBody ReversePaymentAllocationRequest request) {
        return ApiResponse.ok(allocationService.reverse(receiptId, allocationId, request, iamApi.getCurrentUserId()),
                "Allocation reversed; the remaining receipt balance is ready for manual reconciliation.");
    }
}
