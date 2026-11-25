package com.aquasuite.ims.service.finance;

import com.aquasuite.ims.dto.finance.command.PendingCustomerRefundCommand;
import com.aquasuite.ims.dto.finance.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.dto.finance.response.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service quản lý thanh toán hợp nhất (gộp ghi nhận thanh toán, xác minh chuyển khoản,
 * chứng từ thanh toán, giải quyết hoàn tiền và tra cứu nghĩa vụ thu tiền).
 */
public interface PaymentService {

    // ── Order Payment Operations ─────────────────────────────────────────────

    OrderPaymentResponse record(UUID orderId, RecordOrderPaymentRequest request);

    default OrderPaymentResponse ensureOnlinePayosReceiptRecorded(
            UUID orderId,
            UUID payosPaymentId,
            Long payosOrderCode,
            BigDecimal amount,
            OffsetDateTime paidAt,
            UUID actorId) {
        return null;
    }

    OrderPaymentSummary summary(UUID orderId);

    List<OrderPaymentResponse> history(UUID orderId);

    OrderMilestoneProgressResponse milestoneProgress(UUID orderId);

    // ── Payment Verification Operations ──────────────────────────────────────

    PaymentVerificationPageResponse list(UUID orderId, String status, int page, int size);

    PaymentVerificationResponse detail(UUID paymentId);

    PaymentVerificationResponse verify(UUID paymentId);

    PaymentVerificationResponse reject(UUID paymentId, String reason);

    // ── Order Payment Proof Operations ───────────────────────────────────────

    record Proof(String mimeType, byte[] content) {}

    void upload(UUID orderId, UUID paymentId, MultipartFile file) throws IOException;

    Proof read(UUID orderId, UUID paymentId);

    // ── Payment Settlement Operations ────────────────────────────────────────

    VerifiedPaymentSummary getVerifiedPaymentSummary(UUID orderId);

    UUID createPendingCustomerRefund(PendingCustomerRefundCommand command);

    Optional<RefundSettlementView> findCustomerRefund(UUID transactionId);

    // ── Order Receivable Operations ──────────────────────────────────────────

    Optional<OrderReceivableView> getOrderReceivable(UUID orderId);
}
