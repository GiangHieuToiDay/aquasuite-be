package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.error.CodErrorCode;
import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.finance.internal.repository.FinanceLockRepository;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.fulfillment.api.DeliveryCodQueryApi;
import com.aquasuite.ims.module.fulfillment.api.dto.DeliveryCodSnapshot;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Accept means physical receipt of the entire declared amount, not preliminary review. */
@Service
@Validated
@RequiredArgsConstructor
public class CodService {
    private final TransactionRepository transactions;
    private final FinanceLockRepository locks;
    private final SalesOrderApi sales;
    private final DeliveryCodQueryApi deliveries;
    private final IamApi iam;

    @Transactional
    public Collection claim(@NotNull @Valid Claim request) {
        requireRole("SHIPPER");
        UUID actor = actor();
        if (request.collectedAt().isAfter(OffsetDateTime.now().plusMinutes(5))
                || request.amount().signum() <= 0 || request.amount().stripTrailingZeros().scale() > 0) invalid();
        // Same key lock namespace as ordinary payments: keys are shared by transactions.
        locks.lockPaymentKey("order-payment-key:" + request.idempotencyKey());
        var replay = transactions.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            Transaction old = replay.get();
            if (!isCod(old) || !actor.equals(old.getCreatedBy())
                    || !request.deliveryId().equals(old.getDeliveryId())
                    || old.getAmount().compareTo(request.amount()) != 0
                    || !old.getTransactionDate().toInstant().equals(request.collectedAt().toInstant())
                    || !Objects.equals(old.getDescription(), request.note()))
                throw new AppException(CodErrorCode.DUPLICATE_KEY);
            return response(old);
        }
        var first = deliveries.find(request.deliveryId()).orElseThrow(CodService::notFound);
        var order = lockOrder(first.orderId());
        var delivery = deliveries.lock(request.deliveryId()).orElseThrow(CodService::notFound);
        if (!order.orderId().equals(delivery.orderId()) || !actor.equals(delivery.shipperId())) denied();
        requireCodDelivery(delivery);
        List<Transaction> receipts = receipts(order);
        BigDecimal available = order.totalAmount().subtract(sum(receipts,"VERIFIED")).subtract(pendingCod(receipts));
        BigDecimal declared = receipts.stream().filter(CodService::isCod)
                .filter(t -> request.deliveryId().equals(t.getDeliveryId()))
                .filter(t -> List.of("PENDING","VERIFIED").contains(t.getStatus()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if (request.amount().compareTo(available) > 0
                || declared.add(request.amount()).compareTo(delivery.codAmount()) > 0) conflict();
        UUID id = UUID.randomUUID();
        Transaction t = Transaction.builder().transactionId(id).transactionCode("COD-"+id)
                .transactionType("RECEIPT").paymentType("COD").paymentMethod("CASH").category("CUSTOMER_PAYMENT")
                .deliveryId(request.deliveryId()).orderId(order.orderId()).customerId(order.customerId())
                .invoiceId(order.storeInvoiceId()).amount(request.amount()).status("PENDING")
                .idempotencyKey(request.idempotencyKey()).createdBy(actor).transactionDate(request.collectedAt())
                .createdAt(OffsetDateTime.now()).description(request.note()).build();
        transactions.saveAndFlush(t);
        return response(t);
    }

    @Transactional
    public Collection review(UUID id, @NotNull @Valid Review request, boolean accept) {
        requireRole("ACCOUNTANT");
        // Scalar first, then order lock, then entity lock: no stale JPA entity before the lock.
        UUID orderId = transactions.findOrderIdById(id).orElseThrow(CodService::notFound);
        var order = lockOrder(orderId);
        Transaction t = transactions.findByIdForUpdate(id).orElseThrow(CodService::notFound);
        if (!isCod(t) || !orderId.equals(t.getOrderId()) || !Objects.equals(order.customerId(),t.getCustomerId())) conflict();
        UUID actor = actor();
        if (actor.equals(t.getCreatedBy())) denied();
        String target = accept ? "VERIFIED" : "REJECTED";
        if (!accept && (request.reason() == null || request.reason().isBlank())) invalid();
        if (target.equals(t.getStatus())) {
            if (!actor.equals(t.getApprovedBy()) || (!accept && !request.reason().trim().equals(t.getRejectReason()))) conflict();
            return response(t);
        }
        if (!"PENDING".equals(t.getStatus()) || request.expectedVersion() != 0) conflict();
        BigDecimal paid = sum(receipts(order),"VERIFIED");
        if (accept) {
            var delivery = deliveries.lock(t.getDeliveryId()).orElseThrow(CodService::notFound);
            requireCodDelivery(delivery);
            if (!orderId.equals(delivery.orderId()) || t.getAmount().compareTo(delivery.codAmount()) > 0
                    || paid.add(t.getAmount()).compareTo(order.totalAmount()) > 0) conflict();
        }
        t.setStatus(target);
        t.setApprovedBy(actor);
        t.setApprovedAt(OffsetDateTime.now());
        t.setRejectReason(accept ? null : request.reason().trim());
        transactions.saveAndFlush(t);
        if (accept) sales.updatePaymentStatus(orderId,
                paid.add(t.getAmount()).compareTo(order.totalAmount()) == 0 ? "PAID" : "PARTIALLY_PAID");
        return response(t);
    }

    @Transactional(readOnly=true)
    public Page<Collection> list(UUID shipper, UUID order, String status, int page, int size) {
        requireViewer();
        if (!staff()) {
            if (shipper != null && !actor().equals(shipper)) denied();
            shipper = actor();
        }
        if (page < 0 || size < 1 || size > 100
                || (status != null && !List.of("PENDING","VERIFIED","REJECTED").contains(status))) invalid();
        UUID filterShipper = shipper;
        Specification<Transaction> spec = (root,q,cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("transactionType"),"RECEIPT"));
            predicates.add(cb.equal(root.get("paymentType"),"COD"));
            predicates.add(cb.isNotNull(root.get("deliveryId")));
            if (filterShipper != null) predicates.add(cb.equal(root.get("createdBy"),filterShipper));
            if (order != null) predicates.add(cb.equal(root.get("orderId"),order));
            if (status != null) predicates.add(cb.equal(root.get("status"),status));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = transactions.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by("transactionId"))));
        return new Page<>(result.getContent().stream().map(CodService::response).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public Collection detail(UUID id) {
        requireViewer();
        Transaction t = transactions.findById(id).orElseThrow(CodService::notFound);
        if (!isCod(t)) throw notFound();
        if (!staff() && !actor().equals(t.getCreatedBy())) denied();
        return response(t);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Balance balance(UUID orderId) {
        if (!staff()) denied();
        var order = sales.getOrderPaymentInfo(orderId).orElseThrow(CodService::notFound);
        List<Transaction> receipts = receipts(order);
        BigDecimal outstanding = order.totalAmount().subtract(sum(receipts,"VERIFIED")).max(BigDecimal.ZERO);
        BigDecimal pending = pendingCod(receipts);
        return new Balance(orderId,outstanding,pending,outstanding.subtract(pending).max(BigDecimal.ZERO),pending);
    }

    private SalesOrderPaymentInfoDto lockOrder(UUID id) {
        var order = sales.lockOrderPaymentInfo(id).orElseThrow(CodService::notFound);
        if (!order.validForPayment() || order.totalAmount() == null || order.totalAmount().signum() <= 0) conflict();
        return order;
    }
    private List<Transaction> receipts(SalesOrderPaymentInfoDto order) {
        var values = transactions.findByOrderIdAndTransactionTypeIgnoreCase(order.orderId(),"RECEIPT");
        if (values.stream().anyMatch(t -> !Objects.equals(order.customerId(),t.getCustomerId()))) conflict();
        return values;
    }
    private static BigDecimal sum(List<Transaction> values, String status) {
        return values.stream().filter(t -> status.equalsIgnoreCase(t.getStatus())).map(Transaction::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private static BigDecimal pendingCod(List<Transaction> values) {
        return sum(values.stream().filter(CodService::isCod).toList(),"PENDING");
    }
    private static boolean isCod(Transaction t) {
        return "RECEIPT".equals(t.getTransactionType()) && "COD".equals(t.getPaymentType()) && t.getDeliveryId() != null;
    }
    private static Collection response(Transaction t) {
        return new Collection(t.getTransactionId(),t.getDeliveryId(),t.getOrderId(),t.getCustomerId(),t.getCreatedBy(),
                t.getAmount(),t.getStatus(),t.getTransactionDate(),t.getCreatedAt(),t.getApprovedBy(),t.getApprovedAt(),
                t.getRejectReason(),t.getDescription(),"PENDING".equals(t.getStatus()) ? 0 : 1);
    }
    private static void requireCodDelivery(DeliveryCodSnapshot d) {
        if (!"DELIVERED".equals(d.status()) || !d.codRequired() || d.codAmount() == null || d.codAmount().signum() <= 0) conflict();
    }
    private UUID actor() { return Objects.requireNonNull(iam.getCurrentUserId(),"Authenticated actor required"); }
    private static boolean role(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ("ROLE_"+role).equals(a.getAuthority()));
    }
    private static boolean staff() { return role("ACCOUNTANT") || role("SALES_STAFF") || role("SHOP_OWNER"); }
    private static void requireRole(String role) { if (!role(role) && !("ACCOUNTANT".equals(role) && role("SHOP_OWNER"))) denied(); }
    private static void requireViewer() { if (!staff() && !role("SHIPPER")) denied(); }
    private static AppException notFound() { return new AppException(CommonErrorCode.RESOURCE_NOT_FOUND); }
    private static void invalid() { throw new AppException(CodErrorCode.INVALID); }
    private static void conflict() { throw new AppException(CodErrorCode.CONFLICT); }
    private static void denied() { throw new AppException(CommonErrorCode.ACCESS_DENIED); }
}
