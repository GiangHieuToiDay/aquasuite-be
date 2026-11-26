package com.aquasuite.ims.service.finance.impl;

import com.aquasuite.ims.event.finance.CustomerDebtAuditEvent;
import com.aquasuite.ims.dto.finance.command.OrderModificationSettlementCommand;
import com.aquasuite.ims.entity.finance.PaymentInvoiceAllocation;
import com.aquasuite.ims.entity.finance.PaymentReceiptRefundSettlement;
import com.aquasuite.ims.entity.finance.Transaction;
import com.aquasuite.ims.enums.finance.PaymentAllocationStatus;
import com.aquasuite.ims.enums.finance.PaymentAllocationType;
import com.aquasuite.ims.exception.finance.PaymentAllocationErrorCode;
import com.aquasuite.ims.mapper.finance.PaymentAllocationMapper;
import com.aquasuite.ims.dto.finance.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.dto.finance.request.PaymentAllocationLineRequest;
import com.aquasuite.ims.dto.finance.request.ReversePaymentAllocationRequest;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationReceiptView;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationBatchResponse;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationReceiptResponse;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationTargetResponse;
import com.aquasuite.ims.dto.finance.response.PaymentAllocationTargetsResponse;
import com.aquasuite.ims.repository.finance.PaymentInvoiceAllocationRepository;
import com.aquasuite.ims.repository.finance.PaymentReceiptRefundSettlementRepository;
import com.aquasuite.ims.repository.finance.TransactionRepository;
import com.aquasuite.ims.service.finance.PaymentAllocationService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.SalesInvoiceDebtDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-63 owner. It allocates a previously VERIFIED receipt; it never creates or verifies a transaction.
 * The sales order is locked before its receipt, matching UC-23/UC-70's lock order.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentAllocationServiceImpl implements PaymentAllocationService {
    static final String RECEIPT = "RECEIPT";
    static final String VERIFIED = "VERIFIED";
    static final BigDecimal ZERO = BigDecimal.ZERO;

    TransactionRepository transactionRepository;
    PaymentInvoiceAllocationRepository allocationRepository;
    PaymentReceiptRefundSettlementRepository refundSettlementRepository;
    SalesQueryService salesOrderApi;
    SalesQueryService salesReceivableApi;
    PaymentAllocationMapper allocationMapper;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentAllocationReceiptResponse> listUnallocatedReceipts(Pageable pageable) {
        return listReceipts(PaymentAllocationReceiptView.UNALLOCATED, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentAllocationReceiptResponse> listReceipts(PaymentAllocationReceiptView view, Pageable pageable) {
        PaymentAllocationReceiptView resolved = view == null ? PaymentAllocationReceiptView.UNALLOCATED : view;
        Pageable safePageable = com.aquasuite.ims.util.PageableUtils.sanitizePageable(
                pageable, java.util.Set.of("createdAt", "transactionDate", "approvedAt", "amount", "transactionCode"));
        Page<Transaction> receipts = switch (resolved) {
            case UNALLOCATED -> transactionRepository.findVerifiedReceiptsWithRemainingAllocation(
                    PaymentAllocationStatus.ACTIVE, safePageable);
            case ALLOCATED -> transactionRepository.findFullyAllocatedVerifiedReceipts(
                    PaymentAllocationStatus.ACTIVE, safePageable);
            case ALL -> transactionRepository.findAllVerifiedOrderReceipts(safePageable);
        };
        return receipts.map(this::receiptQueueResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAllocationTargetsResponse getTargets(UUID receiptId) {
        Transaction receipt = findReceipt(receiptId);
        SalesOrderPaymentInfoDto order = loadOrder(receipt.getOrderId(), false);
        validateAllocatableReceipt(receipt, order);

        List<PaymentInvoiceAllocation> active = allocationRepository.findByTransactionIdAndAllocationStatus(
                receiptId, PaymentAllocationStatus.ACTIVE);
        BigDecimal allocated = sum(active, PaymentInvoiceAllocation::getAllocatedAmount);
        List<SalesInvoiceDebtDto> invoices = salesReceivableApi.findElectronicallyIntegratedInvoicesByOrderId(order.orderId());
        Map<UUID, BigDecimal> paidByInvoice = currentInvoicePaid(invoices);
        List<PaymentAllocationTargetResponse> targets = invoices.stream()
                .map(invoice -> targetResponse(invoice, paidByInvoice.getOrDefault(invoice.invoiceId(), ZERO)))
                .toList();
        return PaymentAllocationTargetsResponse.builder()
                .receiptId(receipt.getTransactionId()).receiptCode(receipt.getTransactionCode())
                .orderId(order.orderId()).customerId(order.customerId())
                .receiptAmount(amount(receipt.getAmount())).allocatedAmount(allocated)
                .remainingAmount(remainingReceiptAmount(receipt, allocated))
                .invoices(targets).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAllocationLineResponse> getAllocations(UUID receiptId) {
        findReceipt(receiptId);
        return allocationMapper.toResponses(allocationRepository.findByTransactionIdOrderByAllocatedAtDesc(receiptId));
    }

    @Override
    @Transactional
    public void autoAllocateVerifiedReceipt(UUID receiptId, UUID actorId) {
        UUID orderId = transactionRepository.findOrderIdById(receiptId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND));
        SalesOrderPaymentInfoDto order = loadOrder(orderId, true);
        // The first verified receipt may be a deposit. Re-evaluate every receipt in time order so the
        // deposit cap remains stable even when electronic invoices arrive after payment collection.
        allocateAutomatically(order, lockVerifiedReceipts(order), actorId);
    }

    @Override
    @Transactional
    public void autoAllocateVerifiedReceiptsForOrder(UUID orderId, UUID actorId) {
        SalesOrderPaymentInfoDto order = loadOrder(orderId, true);
        allocateAutomatically(order, lockVerifiedReceipts(order), actorId);
    }

    @Override
    @Transactional
    public PaymentAllocationBatchResponse allocate(UUID receiptId, CreatePaymentAllocationRequest request, UUID actorId) {
        // Do not load Transaction before locking its order: doing so can retain an out-of-date persistence-context copy.
        UUID orderId = transactionRepository.findOrderIdById(receiptId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND));
        SalesOrderPaymentInfoDto order = loadOrder(orderId, true);
        Transaction receipt = transactionRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND));
        validateAllocatableReceipt(receipt, order);
        if (!Objects.equals(orderId, receipt.getOrderId())) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        List<PaymentInvoiceAllocation> replay = allocationRepository.findByTransactionIdAndAllocationRequestId(
                receiptId, request.getAllocationRequestId());
        if (!replay.isEmpty()) {
            return idempotentResponseOrFail(receipt, order, request, replay);
        }

        Map<UUID, BigDecimal> requestedByInvoice = requestedByInvoice(request.getAllocations());
        List<SalesInvoiceDebtDto> targets = salesReceivableApi.findInvoicesByIds(requestedByInvoice.keySet());
        if (targets.size() != requestedByInvoice.size()) {
            throw new AppException(PaymentAllocationErrorCode.INVOICE_NOT_FOUND);
        }
        validateTargets(order, targets);

        BigDecimal requested = sum(requestedByInvoice.values());
        List<PaymentInvoiceAllocation> currentReceiptAllocations = allocationRepository
                .findByTransactionIdAndAllocationStatus(receiptId, PaymentAllocationStatus.ACTIVE);
        BigDecimal remainingReceipt = remainingReceiptAmount(receipt,
                sum(currentReceiptAllocations, PaymentInvoiceAllocation::getAllocatedAmount));
        if (requested.compareTo(remainingReceipt) > 0) {
            throw new AppException(PaymentAllocationErrorCode.RECEIPT_BALANCE_EXCEEDED);
        }

        Map<UUID, BigDecimal> paidByInvoice = currentInvoicePaid(targets);
        for (SalesInvoiceDebtDto invoice : targets) {
            BigDecimal invoiceRemaining = positive(amount(invoice.totalAmount())
                    .subtract(paidByInvoice.getOrDefault(invoice.invoiceId(), ZERO)));
            if (requestedByInvoice.get(invoice.invoiceId()).compareTo(invoiceRemaining) > 0) {
                throw new AppException(PaymentAllocationErrorCode.INVOICE_BALANCE_EXCEEDED);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        String reason = trimToNull(request.getReason());
        List<PaymentInvoiceAllocation> rows = request.getAllocations().stream().map(line -> PaymentInvoiceAllocation.builder()
                .allocationRequestId(request.getAllocationRequestId()).transactionId(receiptId).orderId(order.orderId())
                .invoiceId(line.getInvoiceId()).allocatedAmount(line.getAllocatedAmount())
                .allocationType(PaymentAllocationType.MANUAL).allocationStatus(PaymentAllocationStatus.ACTIVE)
                .reason(reason).allocatedBy(actorId).allocatedAt(now).createdAt(now).build()).toList();
        List<PaymentInvoiceAllocation> saved = allocationRepository.saveAllAndFlush(rows);
        BigDecimal remaining = remainingReceipt.subtract(requested);
        publishAllocationAudit(actorId, receipt, order, saved, requested, remaining, reason,
                "PAYMENT_ALLOCATION_CREATED");
        return batchResponse(request.getAllocationRequestId(), receipt, order, requested, remaining, saved);
    }

    @Override
    @Transactional
    public PaymentAllocationLineResponse reverse(
            UUID receiptId, UUID allocationId, ReversePaymentAllocationRequest request, UUID actorId
    ) {
        UUID orderId = transactionRepository.findOrderIdById(receiptId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND));
        SalesOrderPaymentInfoDto order = loadOrder(orderId, true);
        Transaction receipt = transactionRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND));
        validateAllocatableReceipt(receipt, order);
        PaymentInvoiceAllocation allocation = allocationRepository.findByIdForUpdate(allocationId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.ALLOCATION_NOT_FOUND));
        if (!receiptId.equals(allocation.getTransactionId()) || !orderId.equals(allocation.getOrderId())) {
            throw new AppException(PaymentAllocationErrorCode.ALLOCATION_NOT_FOUND);
        }
        if (allocation.getAllocationStatus() != PaymentAllocationStatus.ACTIVE) {
            throw new AppException(PaymentAllocationErrorCode.ALLOCATION_ALREADY_REVERSED);
        }
        if (allocation.getAllocationType() == PaymentAllocationType.REPOINTED) {
            throw new AppException(PaymentAllocationErrorCode.ALLOCATION_NOT_REVERSIBLE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String reason = request.reason().trim();
        allocation.setAllocationStatus(PaymentAllocationStatus.REVERSED);
        allocation.setReversedBy(actorId);
        allocation.setReversedAt(now);
        allocation.setReversalReason(reason);
        PaymentInvoiceAllocation saved = allocationRepository.saveAndFlush(allocation);
        BigDecimal activeAllocated = sum(allocationRepository.findByTransactionIdAndAllocationStatus(
                receiptId, PaymentAllocationStatus.ACTIVE), PaymentInvoiceAllocation::getAllocatedAmount);
        eventPublisher.publishEvent(new CustomerDebtAuditEvent(UUID.randomUUID(), actorId, receiptId,
                "PAYMENT_RECEIPT", "PAYMENT_ALLOCATION_REVERSED",
                Map.of("allocationId", allocationId, "allocatedAmount", amount(saved.getAllocatedAmount())),
                Map.of("orderId", orderId, "remainingReceiptAmount", remainingReceiptAmount(receipt, activeAllocated)),
                reason, Instant.now()));
        return allocationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void reconcileOrderModification(OrderModificationSettlementCommand command) {
        validateModificationCommand(command);
        Map<UUID, SalesOrderPaymentInfoDto> lockedOrders = lockOrdersForModification(command);
        SalesOrderPaymentInfoDto sourceOrder = lockedOrders.get(command.sourceOrderId());
        SalesOrderPaymentInfoDto targetOrder = command.targetOrderId() == null ? null : lockedOrders.get(command.targetOrderId());
        if (targetOrder == null) requireSourceInvoice(command, sourceOrder);
        if (targetOrder != null && !Objects.equals(sourceOrder.customerId(), targetOrder.customerId())) {
            throw new AppException(PaymentAllocationErrorCode.INVALID_REPLACEMENT_SETTLEMENT);
        }

        List<Transaction> receipts = lockVerifiedReceiptsForModification(sourceOrder);
        List<PaymentInvoiceAllocation> sourceAllocations = allocationRepository
                .findByOrderIdAndAllocationStatus(sourceOrder.orderId(), PaymentAllocationStatus.ACTIVE);

        SalesInvoiceDebtDto targetInvoice = targetOrder == null ? null : requireReplacementTarget(command, targetOrder);
        BigDecimal targetRemaining = targetInvoice == null ? ZERO : positive(amount(targetInvoice.totalAmount())
                .subtract(currentInvoicePaid(List.of(targetInvoice)).getOrDefault(targetInvoice.invoiceId(), ZERO)));
        OffsetDateTime now = OffsetDateTime.now();
        String reason = command.reason().trim();
        for (PaymentInvoiceAllocation source : sourceAllocations) {
            source.setAllocationStatus(PaymentAllocationStatus.REVERSED);
            source.setReversedBy(command.actorId());
            source.setReversedAt(now);
            source.setReversalReason(reason);
        }
        if (!sourceAllocations.isEmpty()) allocationRepository.saveAllAndFlush(sourceAllocations);

        List<PaymentInvoiceAllocation> repointed = new ArrayList<>();
        List<PaymentReceiptRefundSettlement> refunded = new ArrayList<>();
        BigDecimal refundRequired = ZERO;
        for (Transaction receipt : receipts) {
            BigDecimal remainingActive = allocationRepository.findByTransactionIdAndAllocationStatus(
                    receipt.getTransactionId(), PaymentAllocationStatus.ACTIVE).stream()
                    .filter(row -> row.getAllocationStatus() == PaymentAllocationStatus.ACTIVE
                            && !Objects.equals(sourceOrder.orderId(), row.getOrderId()))
                    .map(PaymentInvoiceAllocation::getAllocatedAmount)
                    .map(PaymentAllocationServiceImpl::amount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal reusable = positive(amount(receipt.getAmount()).subtract(remainingActive)
                    .subtract(amount(refundSettlementRepository.sumByReceiptTransactionId(receipt.getTransactionId()))));
            if (reusable.signum() <= 0) continue;

            BigDecimal repointedAmount = targetInvoice == null ? ZERO : reusable.min(targetRemaining);
            if (repointedAmount.signum() > 0) {
                repointed.add(PaymentInvoiceAllocation.builder()
                        .allocationRequestId(UUID.randomUUID()).transactionId(receipt.getTransactionId())
                        .orderId(targetOrder.orderId()).invoiceId(targetInvoice.invoiceId()).allocatedAmount(repointedAmount)
                        .allocationType(PaymentAllocationType.REPOINTED).allocationStatus(PaymentAllocationStatus.ACTIVE)
                        .reason(reason).allocatedBy(command.actorId()).allocatedAt(now).createdAt(now).build());
                targetRemaining = targetRemaining.subtract(repointedAmount);
            }
            BigDecimal remainder = reusable.subtract(repointedAmount);
            if (remainder.signum() > 0) {
                refundRequired = refundRequired.add(remainder);
                refunded.add(PaymentReceiptRefundSettlement.builder().receiptTransactionId(receipt.getTransactionId())
                        .refundTransactionId(command.refundTransactionId()).sourceOrderId(sourceOrder.orderId())
                        .settledAmount(remainder).settledBy(command.actorId()).settledAt(now).reason(reason).build());
            }
        }
        if (refundRequired.signum() > 0) validateVerifiedRefund(command, sourceOrder, refundRequired);
        if (!repointed.isEmpty()) allocationRepository.saveAllAndFlush(repointed);
        if (!refunded.isEmpty()) refundSettlementRepository.saveAllAndFlush(refunded);

        eventPublisher.publishEvent(new CustomerDebtAuditEvent(UUID.randomUUID(), command.actorId(), sourceOrder.orderId(),
                "PAYMENT_ALLOCATION", "PAYMENT_ALLOCATION_RECONCILED_FOR_ORDER_MODIFICATION",
                Map.of("sourceOrderId", sourceOrder.orderId(), "sourceAllocationCount", sourceAllocations.size()),
                Map.of("settlementMode", targetOrder == null ? "REFUND" : "REPOINT",
                        "targetOrderId", targetOrder == null ? "NONE" : targetOrder.orderId().toString(),
                        "repointedAmount", sum(repointed, PaymentInvoiceAllocation::getAllocatedAmount),
                        "refundedAmount", refundRequired),
                reason, Instant.now()));
    }

    private Map<UUID, SalesOrderPaymentInfoDto> lockOrdersForModification(OrderModificationSettlementCommand command) {
        List<UUID> orderIds = new ArrayList<>();
        orderIds.add(command.sourceOrderId());
        if (command.targetOrderId() != null && !command.targetOrderId().equals(command.sourceOrderId())) {
            orderIds.add(command.targetOrderId());
        }
        orderIds.sort(UUID::compareTo);
        Map<UUID, SalesOrderPaymentInfoDto> locked = new HashMap<>();
        for (UUID orderId : orderIds) locked.put(orderId, loadOrder(orderId, true));
        return locked;
    }

    private List<Transaction> lockVerifiedReceiptsForModification(SalesOrderPaymentInfoDto sourceOrder) {
        return transactionRepository.findByOrderIdAndTransactionTypeIgnoreCase(sourceOrder.orderId(), RECEIPT).stream()
                .filter(receipt -> VERIFIED.equalsIgnoreCase(receipt.getStatus()))
                .sorted(Comparator.comparing(Transaction::getTransactionDate).thenComparing(Transaction::getTransactionId))
                .map(receipt -> transactionRepository.findByIdForUpdate(receipt.getTransactionId())
                        .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND)))
                .toList();
    }

    private SalesInvoiceDebtDto requireReplacementTarget(
            OrderModificationSettlementCommand command, SalesOrderPaymentInfoDto targetOrder
    ) {
        if (command.targetInvoiceId() == null) {
            throw new AppException(PaymentAllocationErrorCode.INVALID_REPLACEMENT_SETTLEMENT);
        }
        List<SalesInvoiceDebtDto> invoices = salesReceivableApi.findInvoicesByIds(List.of(
                command.sourceInvoiceId(), command.targetInvoiceId()));
        if (invoices.size() != 2) throw new AppException(PaymentAllocationErrorCode.INVOICE_NOT_FOUND);
        Map<UUID, SalesInvoiceDebtDto> indexed = invoices.stream().collect(Collectors.toMap(
                SalesInvoiceDebtDto::invoiceId, Function.identity()));
        SalesInvoiceDebtDto source = indexed.get(command.sourceInvoiceId());
        SalesInvoiceDebtDto target = indexed.get(command.targetInvoiceId());
        if (source == null || target == null || !command.sourceOrderId().equals(source.orderId())
                || !targetOrder.orderId().equals(target.orderId())
                || !Objects.equals(source.customerId(), target.customerId())
                || !isElectronicallyIntegratedInvoice(target)) {
            throw new AppException(PaymentAllocationErrorCode.INVALID_REPLACEMENT_SETTLEMENT);
        }
        return target;
    }

    private void requireSourceInvoice(OrderModificationSettlementCommand command, SalesOrderPaymentInfoDto sourceOrder) {
        SalesInvoiceDebtDto source = salesReceivableApi.findInvoicesByIds(List.of(command.sourceInvoiceId())).stream()
                .findFirst().orElseThrow(() -> new AppException(PaymentAllocationErrorCode.INVOICE_NOT_FOUND));
        if (!sourceOrder.orderId().equals(source.orderId()) || !sourceOrder.customerId().equals(source.customerId())) {
            throw new AppException(PaymentAllocationErrorCode.INVALID_REPLACEMENT_SETTLEMENT);
        }
    }

    private void validateVerifiedRefund(
            OrderModificationSettlementCommand command, SalesOrderPaymentInfoDto sourceOrder, BigDecimal refundRequired
    ) {
        if (command.refundTransactionId() == null) {
            throw new AppException(PaymentAllocationErrorCode.REFUND_NOT_VERIFIED);
        }
        Transaction refund = transactionRepository.findByIdForUpdate(command.refundTransactionId())
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.REFUND_NOT_VERIFIED));
        boolean validRefund = "EXPENSE".equalsIgnoreCase(refund.getTransactionType())
                && "CUSTOMER_REFUND".equalsIgnoreCase(refund.getCategory())
                && VERIFIED.equalsIgnoreCase(refund.getStatus())
                && Objects.equals(sourceOrder.orderId(), refund.getOrderId())
                && Objects.equals(sourceOrder.customerId(), refund.getCustomerId());
        BigDecimal unusedRefund = positive(amount(refund.getAmount()).subtract(
                amount(refundSettlementRepository.sumByRefundTransactionId(refund.getTransactionId()))));
        if (!validRefund || unusedRefund.compareTo(refundRequired) < 0) {
            throw new AppException(PaymentAllocationErrorCode.REFUND_NOT_VERIFIED);
        }
    }

    private void validateModificationCommand(OrderModificationSettlementCommand command) {
        if (command == null || command.sourceOrderId() == null || command.sourceInvoiceId() == null
                || command.actorId() == null || trimToNull(command.reason()) == null
                || (command.targetOrderId() == null) != (command.targetInvoiceId() == null)) {
            throw new AppException(PaymentAllocationErrorCode.INVALID_REPLACEMENT_SETTLEMENT);
        }
    }

    /** Applies one-stage receipts in full, or only the configured deposit portion for staged orders. */
    private void allocateAutomatically(SalesOrderPaymentInfoDto order, List<Transaction> receipts, UUID actorId) {
        if (receipts.isEmpty() || actorId == null) return;
        List<SalesInvoiceDebtDto> invoices = salesReceivableApi
                .findElectronicallyIntegratedInvoicesByOrderId(order.orderId());
        if (invoices.isEmpty()) return;

        Map<UUID, BigDecimal> paidByInvoice = currentInvoicePaid(invoices);
        Map<UUID, List<PaymentInvoiceAllocation>> rowsByReceipt = new LinkedHashMap<>();
        Map<UUID, BigDecimal> allocatedByReceipt = new HashMap<>();
        Map<UUID, BigDecimal> remainingByReceipt = new HashMap<>();
        Map<UUID, BigDecimal> automaticLimitByReceipt = automaticLimitByReceipt(order, receipts);
        OffsetDateTime now = OffsetDateTime.now();

        for (Transaction receipt : receipts) {
            BigDecimal automaticLimit = automaticLimitByReceipt.getOrDefault(receipt.getTransactionId(), ZERO);
            if (automaticLimit.signum() <= 0) continue;
            List<PaymentInvoiceAllocation> allocationHistory = allocationRepository
                    .findByTransactionIdOrderByAllocatedAtDesc(receipt.getTransactionId());
            // A corrected/manual receipt belongs to the Accountant afterwards; do not silently undo that intent.
            if (allocationHistory.stream().anyMatch(row -> row.getAllocationStatus() == PaymentAllocationStatus.REVERSED
                    || row.getAllocationType() == PaymentAllocationType.MANUAL
                    || row.getAllocationType() == PaymentAllocationType.REPOINTED)) {
                continue;
            }
            List<PaymentInvoiceAllocation> activeReceiptAllocations = allocationRepository
                    .findByTransactionIdAndAllocationStatus(receipt.getTransactionId(), PaymentAllocationStatus.ACTIVE);
            BigDecimal alreadyAllocated = sum(activeReceiptAllocations, PaymentInvoiceAllocation::getAllocatedAmount);
            BigDecimal alreadyAutomaticallyAllocated = activeReceiptAllocations.stream()
                    .filter(row -> PaymentAllocationType.AUTO_ADVANCE == row.getAllocationType())
                    .map(PaymentInvoiceAllocation::getAllocatedAmount)
                    .map(PaymentAllocationServiceImpl::amount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal remainingReceipt = remainingReceiptAmount(receipt, alreadyAllocated)
                    .min(positive(automaticLimit.subtract(alreadyAutomaticallyAllocated)));
            if (remainingReceipt.signum() <= 0) continue;

            UUID requestId = UUID.randomUUID();
            List<PaymentInvoiceAllocation> rows = new ArrayList<>();
            for (SalesInvoiceDebtDto invoice : invoices) {
                BigDecimal invoiceRemaining = positive(amount(invoice.totalAmount())
                        .subtract(paidByInvoice.getOrDefault(invoice.invoiceId(), ZERO)));
                if (invoiceRemaining.signum() <= 0 || remainingReceipt.signum() <= 0) continue;
                BigDecimal allocated = remainingReceipt.min(invoiceRemaining);
                rows.add(PaymentInvoiceAllocation.builder()
                        .allocationRequestId(requestId).transactionId(receipt.getTransactionId()).orderId(order.orderId())
                        .invoiceId(invoice.invoiceId()).allocatedAmount(allocated)
                        .allocationType(PaymentAllocationType.AUTO_ADVANCE).allocationStatus(PaymentAllocationStatus.ACTIVE)
                        .reason(order.stagedDelivery()
                                ? "Tự động phân bổ FIFO phần tiền cọc"
                                : "Tự động phân bổ khoản thu cho đơn một đợt")
                        .allocatedBy(actorId).allocatedAt(now).createdAt(now).build());
                paidByInvoice.merge(invoice.invoiceId(), allocated, BigDecimal::add);
                remainingReceipt = remainingReceipt.subtract(allocated);
            }
            if (!rows.isEmpty()) {
                rowsByReceipt.put(receipt.getTransactionId(), rows);
                allocatedByReceipt.put(receipt.getTransactionId(), sum(rows, PaymentInvoiceAllocation::getAllocatedAmount));
                remainingByReceipt.put(receipt.getTransactionId(), remainingReceipt);
            }
        }
        if (rowsByReceipt.isEmpty()) return;

        List<PaymentInvoiceAllocation> saved = allocationRepository.saveAllAndFlush(
                rowsByReceipt.values().stream().flatMap(Collection::stream).toList());
        Map<UUID, List<PaymentInvoiceAllocation>> savedByReceipt = saved.stream()
                .collect(Collectors.groupingBy(PaymentInvoiceAllocation::getTransactionId));
        for (Transaction receipt : receipts) {
            List<PaymentInvoiceAllocation> receiptRows = savedByReceipt.get(receipt.getTransactionId());
            if (receiptRows == null) continue;
            publishAllocationAudit(actorId, receipt, order, receiptRows,
                    allocatedByReceipt.get(receipt.getTransactionId()), remainingByReceipt.get(receipt.getTransactionId()),
                    order.stagedDelivery() ? "Tự động phân bổ FIFO phần tiền cọc"
                            : "Tự động phân bổ khoản thu cho đơn một đợt",
                    "PAYMENT_ALLOCATION_AUTO_CREATED");
        }
    }

    /** Locks every verified receipt after the order lock, preserving the same lock order as manual allocation. */
    private List<Transaction> lockVerifiedReceipts(SalesOrderPaymentInfoDto order) {
        List<Transaction> lockedReceipts = transactionRepository
                .findByOrderIdAndTransactionTypeIgnoreCase(order.orderId(), RECEIPT).stream()
                .filter(receipt -> VERIFIED.equalsIgnoreCase(receipt.getStatus()))
                .sorted(Comparator.comparing(Transaction::getTransactionDate)
                        .thenComparing(Transaction::getTransactionId))
                .map(receipt -> transactionRepository.findByIdForUpdate(receipt.getTransactionId())
                        .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND)))
                .toList();
        for (Transaction receipt : lockedReceipts) {
            validateAllocatableReceipt(receipt, order);
        }
        return lockedReceipts;
    }

    /**
     * A one-stage order can settle all verified money automatically. A staged order may settle only
     * its earliest verified money up to the immutable deposit amount; later money remains manual work.
     */
    private Map<UUID, BigDecimal> automaticLimitByReceipt(SalesOrderPaymentInfoDto order, List<Transaction> receipts) {
        Map<UUID, BigDecimal> limits = new LinkedHashMap<>();
        if (!order.stagedDelivery()) {
            receipts.forEach(receipt -> limits.put(receipt.getTransactionId(), amount(receipt.getAmount())));
            return limits;
        }
        BigDecimal remainingDeposit = positive(amount(order.depositAmount()));
        for (Transaction receipt : receipts) {
            if (remainingDeposit.signum() <= 0) break;
            BigDecimal eligible = amount(receipt.getAmount()).min(remainingDeposit);
            limits.put(receipt.getTransactionId(), eligible);
            remainingDeposit = remainingDeposit.subtract(eligible);
        }
        return limits;
    }

    private PaymentAllocationBatchResponse idempotentResponseOrFail(Transaction receipt, SalesOrderPaymentInfoDto order,
                                                                      CreatePaymentAllocationRequest request,
                                                                      List<PaymentInvoiceAllocation> existing) {
        Map<UUID, BigDecimal> requested = requestedByInvoice(request.getAllocations());
        Map<UUID, BigDecimal> recorded = existing.stream().collect(Collectors.groupingBy(PaymentInvoiceAllocation::getInvoiceId,
                Collectors.reducing(ZERO, PaymentInvoiceAllocation::getAllocatedAmount, BigDecimal::add)));
        boolean sameReason = existing.stream().allMatch(row -> Objects.equals(trimToNull(request.getReason()), row.getReason()));
        if (!sameReason || requested.size() != recorded.size() || requested.entrySet().stream()
                .anyMatch(entry -> entry.getValue().compareTo(recorded.getOrDefault(entry.getKey(), ZERO)) != 0)) {
            throw new AppException(PaymentAllocationErrorCode.ALLOCATION_REQUEST_CONFLICT);
        }
        BigDecimal total = sum(existing, PaymentInvoiceAllocation::getAllocatedAmount);
        BigDecimal totalAllocated = sum(allocationRepository.findByTransactionIdAndAllocationStatus(
                receipt.getTransactionId(), PaymentAllocationStatus.ACTIVE), PaymentInvoiceAllocation::getAllocatedAmount);
        return batchResponse(request.getAllocationRequestId(), receipt, order, total,
                remainingReceiptAmount(receipt, totalAllocated), existing);
    }

    private Transaction findReceipt(UUID receiptId) {
        return transactionRepository.findById(receiptId)
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_FOUND));
    }

    private SalesOrderPaymentInfoDto loadOrder(UUID orderId, boolean lock) {
        if (orderId == null) {
            throw new AppException(PaymentAllocationErrorCode.ORDER_NOT_FOUND);
        }
        return (lock ? salesOrderApi.lockOrderPaymentInfo(orderId) : salesOrderApi.getOrderPaymentInfo(orderId))
                .orElseThrow(() -> new AppException(PaymentAllocationErrorCode.ORDER_NOT_FOUND));
    }

    private void validateAllocatableReceipt(Transaction receipt, SalesOrderPaymentInfoDto order) {
        if (!RECEIPT.equalsIgnoreCase(receipt.getTransactionType()) || !VERIFIED.equalsIgnoreCase(receipt.getStatus())
                || receipt.getOrderId() == null || receipt.getAmount() == null || receipt.getAmount().signum() <= 0) {
            throw new AppException(PaymentAllocationErrorCode.RECEIPT_NOT_ALLOCATABLE);
        }
        if (!order.validForPayment()) {
            throw new AppException(PaymentAllocationErrorCode.ORDER_NOT_ELIGIBLE);
        }
        if (!Objects.equals(order.customerId(), receipt.getCustomerId())) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        // transaction.invoiceId is the source Store invoice recorded by UC-23/UC-70, not settlement proof.
        // Only PaymentInvoiceAllocation rows settle a receipt to a delivery invoice.
    }

    private Map<UUID, BigDecimal> requestedByInvoice(List<PaymentAllocationLineRequest> lines) {
        Set<UUID> ids = new HashSet<>();
        Map<UUID, BigDecimal> values = new HashMap<>();
        for (PaymentAllocationLineRequest line : lines) {
            if (!ids.add(line.getInvoiceId())) {
                throw new AppException(PaymentAllocationErrorCode.DUPLICATE_INVOICE_LINE);
            }
            values.put(line.getInvoiceId(), line.getAllocatedAmount());
        }
        return values;
    }

    private void validateTargets(SalesOrderPaymentInfoDto order, List<SalesInvoiceDebtDto> targets) {
        for (SalesInvoiceDebtDto target : targets) {
            if (!Objects.equals(order.orderId(), target.orderId()) || !Objects.equals(order.customerId(), target.customerId())
                    || !isElectronicallyIntegratedInvoice(target)) {
                throw new AppException(PaymentAllocationErrorCode.INVALID_ALLOCATION_TARGET);
            }
        }
    }

    private Map<UUID, BigDecimal> currentInvoicePaid(List<SalesInvoiceDebtDto> invoices) {
        if (invoices.isEmpty()) return Map.of();
        Set<UUID> invoiceIds = invoices.stream().map(SalesInvoiceDebtDto::invoiceId).collect(Collectors.toSet());
        Map<UUID, BigDecimal> paid = new HashMap<>();
        allocationRepository.findByInvoiceIdInAndAllocationStatus(invoiceIds, PaymentAllocationStatus.ACTIVE).forEach(row ->
                paid.merge(row.getInvoiceId(), amount(row.getAllocatedAmount()), BigDecimal::add));
        // transaction.invoiceId is not an implicit allocation; only UC-63 rows settle a receipt.
        return paid;
    }

    private PaymentAllocationTargetResponse targetResponse(SalesInvoiceDebtDto invoice, BigDecimal paid) {
        return PaymentAllocationTargetResponse.builder().invoiceId(invoice.invoiceId()).invoiceCode(invoice.invoiceCode())
                .invoiceSymbol(invoice.invoiceSymbol())
                .issueDate(invoice.signedAt() != null ? invoice.signedAt() : invoice.issueDate()).dueDate(invoice.dueDate())
                .totalAmount(amount(invoice.totalAmount())).verifiedPaidAmount(paid)
                .outstandingAmount(positive(amount(invoice.totalAmount()).subtract(paid))).build();
    }

    private PaymentAllocationReceiptResponse receiptQueueResponse(Transaction receipt) {
        BigDecimal allocated = sum(allocationRepository.findByTransactionIdAndAllocationStatus(
                receipt.getTransactionId(), PaymentAllocationStatus.ACTIVE), PaymentInvoiceAllocation::getAllocatedAmount);
        return new PaymentAllocationReceiptResponse(receipt.getTransactionId(), receipt.getTransactionCode(), receipt.getOrderId(),
                receipt.getCustomerId(), amount(receipt.getAmount()), allocated,
                remainingReceiptAmount(receipt, allocated), receipt.getPaymentMethod(), receipt.getTransactionDate());
    }

    private PaymentAllocationBatchResponse batchResponse(UUID requestId, Transaction receipt, SalesOrderPaymentInfoDto order,
                                                          BigDecimal amount, BigDecimal remaining,
                                                          List<PaymentInvoiceAllocation> rows) {
        return PaymentAllocationBatchResponse.builder().allocationRequestId(requestId).receiptId(receipt.getTransactionId())
                .orderId(order.orderId()).allocatedAmount(amount).remainingReceiptAmount(remaining)
                .allocations(allocationMapper.toResponses(rows)).build();
    }

    private void publishAllocationAudit(UUID actorId, Transaction receipt, SalesOrderPaymentInfoDto order,
                                        List<PaymentInvoiceAllocation> rows, BigDecimal allocated,
                                        BigDecimal remaining, String reason, String action) {
        List<UUID> invoiceIds = rows.stream().map(PaymentInvoiceAllocation::getInvoiceId).toList();
        eventPublisher.publishEvent(new CustomerDebtAuditEvent(UUID.randomUUID(), actorId, receipt.getTransactionId(),
                "PAYMENT_RECEIPT", action,
                Map.of("receiptAmount", amount(receipt.getAmount())),
                Map.of("orderId", order.orderId(), "invoiceIds", invoiceIds, "allocatedAmount", allocated,
                        "remainingReceiptAmount", remaining), reason, Instant.now()));
    }

    private static boolean isElectronicallyIntegratedInvoice(SalesInvoiceDebtDto invoice) {
        boolean operationallyLocked = "ISSUED".equalsIgnoreCase(invoice.status())
                || "DISPATCHED".equalsIgnoreCase(invoice.status());
        return operationallyLocked
                && invoice.providerInvoiceId() != null
                && !invoice.providerInvoiceId().isBlank()
                && invoice.signedAt() != null;
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().map(PaymentAllocationServiceImpl::amount).reduce(ZERO, BigDecimal::add);
    }

    private static <T> BigDecimal sum(Collection<T> values, Function<T, BigDecimal> mapper) {
        return values.stream().map(mapper).map(PaymentAllocationServiceImpl::amount).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal amount(BigDecimal value) { return value == null ? ZERO : value; }
    private BigDecimal remainingReceiptAmount(Transaction receipt, BigDecimal activeAllocated) {
        BigDecimal refunded = amount(refundSettlementRepository.sumByReceiptTransactionId(receipt.getTransactionId()));
        return positive(amount(receipt.getAmount()).subtract(amount(activeAllocated)).subtract(refunded));
    }
    private static BigDecimal positive(BigDecimal value) { return value.max(ZERO); }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
