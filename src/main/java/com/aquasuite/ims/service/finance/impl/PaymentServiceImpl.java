package com.aquasuite.ims.service.finance.impl;

import com.aquasuite.ims.dto.finance.command.PendingCustomerRefundCommand;
import com.aquasuite.ims.dto.finance.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.dto.finance.response.*;
import com.aquasuite.ims.service.finance.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Primary
public class PaymentServiceImpl implements PaymentService {

    private final OrderPaymentServiceImpl orderPaymentService;
    private final PaymentVerificationServiceImpl verificationService;
    private final OrderPaymentProofServiceImpl proofService;
    private final PaymentSettlementServiceImpl settlementService;
    private final OrderReceivableServiceImpl receivableService;

    @Autowired
    public PaymentServiceImpl(
            @Autowired(required = false) OrderPaymentServiceImpl orderPaymentService,
            @Autowired(required = false) PaymentVerificationServiceImpl verificationService,
            @Autowired(required = false) OrderPaymentProofServiceImpl proofService,
            @Autowired(required = false) PaymentSettlementServiceImpl settlementService,
            @Autowired(required = false) OrderReceivableServiceImpl receivableService) {
        this.orderPaymentService = orderPaymentService;
        this.verificationService = verificationService;
        this.proofService = proofService;
        this.settlementService = settlementService;
        this.receivableService = receivableService;
    }

    // ── OrderPayment Operations ──────────────────────────────────────────────

    @Override
    public OrderPaymentResponse record(UUID orderId, RecordOrderPaymentRequest request) {
        return orderPaymentService.record(orderId, request);
    }

    @Override
    public OrderPaymentSummary summary(UUID orderId) {
        return orderPaymentService.summary(orderId);
    }

    @Override
    public List<OrderPaymentResponse> history(UUID orderId) {
        return orderPaymentService.history(orderId);
    }

    @Override
    public OrderMilestoneProgressResponse milestoneProgress(UUID orderId) {
        return orderPaymentService.milestoneProgress(orderId);
    }

    // ── PaymentVerification Operations ───────────────────────────────────────

    @Override
    public PaymentVerificationPageResponse list(UUID orderId, String status, int page, int size) {
        return verificationService.list(orderId, status, page, size);
    }

    @Override
    public PaymentVerificationResponse detail(UUID paymentId) {
        return verificationService.detail(paymentId);
    }

    @Override
    public PaymentVerificationResponse verify(UUID paymentId) {
        return verificationService.verify(paymentId);
    }

    @Override
    public PaymentVerificationResponse reject(UUID paymentId, String reason) {
        return verificationService.reject(paymentId, reason);
    }

    // ── OrderPaymentProof Operations ─────────────────────────────────────────

    @Override
    public void upload(UUID orderId, UUID paymentId, MultipartFile file) throws IOException {
        proofService.upload(orderId, paymentId, file);
    }

    @Override
    public Proof read(UUID orderId, UUID paymentId) {
        return proofService.read(orderId, paymentId);
    }

    // ── PaymentSettlement Operations ─────────────────────────────────────────

    @Override
    public VerifiedPaymentSummary getVerifiedPaymentSummary(UUID orderId) {
        return settlementService.getVerifiedPaymentSummary(orderId);
    }

    @Override
    public UUID createPendingCustomerRefund(PendingCustomerRefundCommand command) {
        return settlementService.createPendingCustomerRefund(command);
    }

    @Override
    public Optional<RefundSettlementView> findCustomerRefund(UUID transactionId) {
        return settlementService.findCustomerRefund(transactionId);
    }

    // ── OrderReceivable Operations ───────────────────────────────────────────

    @Override
    public Optional<OrderReceivableView> getOrderReceivable(UUID orderId) {
        return receivableService.getOrderReceivable(orderId);
    }
}
