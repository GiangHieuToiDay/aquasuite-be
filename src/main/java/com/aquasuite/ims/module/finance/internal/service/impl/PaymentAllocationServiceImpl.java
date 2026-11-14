package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.api.event.CustomerDebtAuditEvent;
import com.aquasuite.ims.module.finance.internal.entity.PaymentInvoiceAllocation;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationStatus;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationType;
import com.aquasuite.ims.module.finance.internal.error.PaymentAllocationErrorCode;
import com.aquasuite.ims.module.finance.internal.mapper.PaymentAllocationMapper;
import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.PaymentAllocationLineRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationBatchResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationTargetResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationTargetsResponse;
import com.aquasuite.ims.module.finance.internal.repository.PaymentInvoiceAllocationRepository;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.service.PaymentAllocationService;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.SalesReceivableApi;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceDebtDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    static final String STORE = "STORE";
    static final BigDecimal ZERO = BigDecimal.ZERO;

    TransactionRepository transactionRepository;
    PaymentInvoiceAllocationRepository allocationRepository;
    SalesOrderApi salesOrderApi;
    SalesReceivableApi salesReceivableApi;
    PaymentAllocationMapper allocationMapper;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PaymentAllocationTargetsResponse getTargets(UUID receiptId) {
        Transaction receipt = findReceipt(receiptId);
        SalesOrderPaymentInfoDto order = loadOrder(receipt.getOrderId(), false);
        validateAllocatableReceipt(receipt, order);

        List<PaymentInvoiceAllocation> active = allocationRepository.findByTransactionIdAndAllocationStatus(
                receiptId, PaymentAllocationStatus.ACTIVE);
        BigDecimal allocated = sum(active, PaymentInvoiceAllocation::getAllocatedAmount);
        List<SalesInvoiceDebtDto> invoices = salesReceivableApi.findElectronicallyIssuedStoreInvoicesByOrderId(order.orderId());
        Map<UUID, BigDecimal> paidByInvoice = currentInvoicePaid(invoices);
        List<PaymentAllocationTargetResponse> targets = invoices.stream()
                .map(invoice -> targetResponse(invoice, paidByInvoice.getOrDefault(invoice.invoiceId(), ZERO)))
                .toList();
        return PaymentAllocationTargetsResponse.builder()
                .receiptId(receipt.getTransactionId()).receiptCode(receipt.getTransactionCode())
                .orderId(order.orderId()).customerId(order.customerId())
                .receiptAmount(amount(receipt.getAmount())).allocatedAmount(allocated)
                .remainingAmount(positive(amount(receipt.getAmount()).subtract(allocated)))
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
        BigDecimal remainingReceipt = positive(amount(receipt.getAmount())
                .subtract(sum(currentReceiptAllocations, PaymentInvoiceAllocation::getAllocatedAmount)));
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
        publishAllocationAudit(actorId, receipt, order, saved, requested, remaining, reason);
        return batchResponse(request.getAllocationRequestId(), receipt, order, requested, remaining, saved);
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
                positive(amount(receipt.getAmount()).subtract(totalAllocated)), existing);
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
        // A receipt linked to any Store invoice remains an order-level receipt until UC-63 writes allocation rows.
        // The exception is a historical direct link to an old non-STORE electronic invoice.
        if (receipt.getInvoiceId() != null) {
            List<SalesInvoiceDebtDto> linked = salesReceivableApi.findInvoicesByIds(List.of(receipt.getInvoiceId()));
            if (!linked.isEmpty() && !STORE.equalsIgnoreCase(linked.getFirst().invoiceType())) {
                throw new AppException(PaymentAllocationErrorCode.RECEIPT_ALREADY_DIRECTLY_LINKED);
            }
        }
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
                    || !isElectronicallyIssuedStoreInvoice(target)) {
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
        // A receipt linked to a Store invoice is not an implicit allocation. Only UC-63 allocation rows settle it.
        // Historical non-Store direct links are not returned in the target set and remain outside this new flow.
        return paid;
    }

    private PaymentAllocationTargetResponse targetResponse(SalesInvoiceDebtDto invoice, BigDecimal paid) {
        return PaymentAllocationTargetResponse.builder().invoiceId(invoice.invoiceId()).invoiceCode(invoice.invoiceCode())
                .invoiceSymbol(invoice.invoiceSymbol())
                .issueDate(invoice.signedAt() != null ? invoice.signedAt() : invoice.issueDate()).dueDate(invoice.dueDate())
                .totalAmount(amount(invoice.totalAmount())).verifiedPaidAmount(paid)
                .outstandingAmount(positive(amount(invoice.totalAmount()).subtract(paid))).build();
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
                                        BigDecimal remaining, String reason) {
        List<UUID> invoiceIds = rows.stream().map(PaymentInvoiceAllocation::getInvoiceId).toList();
        eventPublisher.publishEvent(new CustomerDebtAuditEvent(UUID.randomUUID(), actorId, receipt.getTransactionId(),
                "PAYMENT_RECEIPT", "PAYMENT_ALLOCATION_CREATED",
                Map.of("receiptAmount", amount(receipt.getAmount())),
                Map.of("orderId", order.orderId(), "invoiceIds", invoiceIds, "allocatedAmount", allocated,
                        "remainingReceiptAmount", remaining), reason, Instant.now()));
    }

    private static boolean isElectronicallyIssuedStoreInvoice(SalesInvoiceDebtDto invoice) {
        boolean operationallyLocked = "ISSUED".equalsIgnoreCase(invoice.status())
                || "DISPATCHED".equalsIgnoreCase(invoice.status());
        return STORE.equalsIgnoreCase(invoice.invoiceType())
                && operationallyLocked
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
    private static BigDecimal positive(BigDecimal value) { return value.max(ZERO); }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
