package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.payload.request.RejectPaymentRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentVerificationPageResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentVerificationResponse;
import com.aquasuite.ims.module.finance.internal.service.PaymentVerificationService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API UC-70 — chỉ Accountant xem, xác minh hoặc từ chối khoản chuyển khoản đang chờ.
 * Backend lấy người xử lý từ phiên đăng nhập; không nhận actorId từ client.
 */
@RestController
@RequestMapping("/api/v1/finance/payment-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
public class PaymentVerificationController {

    /** Xử lý nghiệp vụ xem/xác minh/từ chối khoản chờ. */
    private final PaymentVerificationService service;

    /**
     * Danh sách khoản chuyển khoản đang chờ xác minh, lọc tùy chọn theo đơn.
     * @param orderId mã đơn để lọc (không bắt buộc)
     * @param page chỉ số trang (từ 0)
     * @param size kích thước trang
     * @return trang các khoản đang chờ
     */
    @GetMapping
    public ApiResponse<PaymentVerificationPageResponse> list(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(orderId, status, page, size));
    }

    /**
     * Chi tiết một khoản chuyển khoản để kế toán đối chiếu.
     * @param paymentId mã giao dịch
     * @return thông tin khoản
     */
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentVerificationResponse> detail(@PathVariable UUID paymentId) {
        return ApiResponse.ok(service.detail(paymentId));
    }

    /**
     * Xác minh một khoản đang chờ; tính vào tiền đã thu nếu không vượt nghĩa vụ.
     * @param paymentId mã giao dịch cần xác minh
     * @return khoản sau khi xác minh
     */
    @PostMapping("/{paymentId}/verify")
    public ApiResponse<PaymentVerificationResponse> verify(@PathVariable UUID paymentId) {
        return ApiResponse.ok(service.verify(paymentId));
    }

    /**
     * Từ chối một khoản đang chờ với lý do bắt buộc.
     * @param paymentId mã giao dịch cần từ chối
     * @param request lý do từ chối
     * @return khoản sau khi từ chối
     */
    @PostMapping("/{paymentId}/reject")
    public ApiResponse<PaymentVerificationResponse> reject(@PathVariable UUID paymentId,
            @Valid @RequestBody RejectPaymentRequest request) {
        return ApiResponse.ok(service.reject(paymentId, request.reason()));
    }
}
