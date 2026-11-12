package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderMilestoneProgressResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderPaymentResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderPaymentSummary;
import com.aquasuite.ims.module.finance.internal.service.OrderPaymentService;
import com.aquasuite.ims.module.finance.internal.service.OrderPaymentProofService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** API nội bộ cho nhân viên ghi nhận, theo dõi thanh toán và quản lý chứng từ của đơn. */
@RestController
@RequestMapping("/api/v1/finance/orders/{orderId}")
@RequiredArgsConstructor
public class OrderPaymentController {
    /** Xử lý khoản thu và tính tổng thanh toán của đơn. */
    private final OrderPaymentService service;
    /** Lưu và tải chứng từ của khoản chuyển khoản. */
    private final OrderPaymentProofService proofService;

    /**
     * Ghi một khoản tiền mặt hoặc chuyển khoản vào đơn theo yêu cầu của nhân viên.
     * @param orderId mã đơn nhận thanh toán
     * @param request số tiền, phương thức và mã chống ghi trùng
     * @return thông tin giao dịch đã ghi hoặc giao dịch tương ứng khi gửi lại hợp lệ
     */
    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<OrderPaymentResponse> record(@PathVariable UUID orderId,
            @Valid @RequestBody RecordOrderPaymentRequest request) {
        return ApiResponse.ok(service.record(orderId, request));
    }

    /**
     * Trả danh sách giao dịch thu tiền đã ghi cho đơn, gồm cả khoản đang chờ.
     * @param orderId mã đơn cần xem
     * @return danh sách giao dịch thu tiền
     */
    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ApiResponse<List<OrderPaymentResponse>> history(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.history(orderId));
    }

    /**
     * Trả tổng đơn, số đã xác minh, số đang chờ và số còn phải trả.
     * @param orderId mã đơn cần xem
     * @return tổng quan và trạng thái thanh toán của đơn
     */
    @GetMapping("/payment-summary")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ApiResponse<OrderPaymentSummary> summary(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.summary(orderId));
    }

    /**
     * Trả tiến độ thanh toán theo từng mốc của đơn, cùng số liệu toàn đơn.
     * Chỉ khoản đã xác minh được phân bổ vào các mốc theo thứ tự; khoản đang chờ hiển thị riêng.
     * @param orderId mã đơn cần xem tiến độ
     * @return tiến độ từng mốc và tổng quan thanh toán của đơn
     */
    @GetMapping("/payment-milestones")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ApiResponse<OrderMilestoneProgressResponse> milestoneProgress(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.milestoneProgress(orderId));
    }

    /**
     * Cho nhân viên có quyền đính kèm chứng từ vào khoản chuyển khoản đang chờ.
     * @param orderId mã đơn sở hữu khoản thu
     * @param paymentId mã khoản chuyển khoản
     * @param file tệp chứng từ được gửi dưới dạng multipart
     * @return phản hồi thành công không có dữ liệu
     * @throws IOException khi không đọc được tệp
     */
    @PostMapping(value = "/payments/{paymentId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Void> uploadProof(@PathVariable UUID orderId, @PathVariable UUID paymentId,
            @RequestPart("file") MultipartFile file) throws IOException {
        proofService.upload(orderId, paymentId, file);
        return ApiResponse.ok(null);
    }

    /**
     * Tải chứng từ của đúng giao dịch và đơn hàng khi người gọi có quyền xem.
     * @param orderId mã đơn sở hữu khoản thu
     * @param paymentId mã khoản chuyển khoản
     * @return nội dung tệp và loại nội dung để tải xuống
     */
    @GetMapping("/payments/{paymentId}/proof")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER','ACCOUNTANT')")
    public ResponseEntity<byte[]> readProof(@PathVariable UUID orderId, @PathVariable UUID paymentId) {
        var proof = proofService.read(orderId, paymentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(proof.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payment-proof\"")
                .header("X-Content-Type-Options", "nosniff").body(proof.content());
    }
}
