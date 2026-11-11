package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.error.CodErrorCode;
import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.finance.internal.repository.CodRepository;
import com.aquasuite.ims.module.finance.internal.repository.FinanceLockRepository;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.fulfillment.api.DeliveryCodQueryApi;
import com.aquasuite.ims.module.fulfillment.api.dto.DeliveryCodSnapshot;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Collection declarations do not pay orders; only counted remittances create receipts. */
@Service
@RequiredArgsConstructor
public class CodService {
    private final CodRepository cod;
    private final TransactionRepository transactions;
    private final FinanceLockRepository locks;
    private final SalesOrderApi sales;
    private final DeliveryCodQueryApi deliveries;
    private final IamApi iam;
    private final ObjectMapper json;

    @Transactional
    public Collection claim(Claim request) {
        requireRole("SHIPPER");
        UUID actor=actor();
        if (request.collectedAt().isAfter(OffsetDateTime.now().plusMinutes(5))) invalid();
        String hash=hash(List.of(actor,request));
        locks.lockPaymentKey("cod-claim:"+request.idempotencyKey());
        var replay=cod.byKey(request.idempotencyKey());
        if (!replay.isEmpty()) {
            Collection old=replay.getFirst();
            if (!actor.equals(old.shipperId()) || !hash.equals(cod.claimHash(old.collectionId())))
                throw new AppException(CodErrorCode.DUPLICATE_KEY);
            return old;
        }
        var first=deliveries.find(request.deliveryId()).orElseThrow(CodService::notFound);
        var order=lockOrder(first.orderId());
        var delivery=deliveries.lock(request.deliveryId()).orElseThrow(CodService::notFound);
        if (!order.orderId().equals(delivery.orderId()) || !actor.equals(delivery.shipperId())) denied();
        requireCodDelivery(delivery);
        BigDecimal available=remaining(order).subtract(cod.held(order.orderId())).subtract(cod.pending(order.orderId()));
        if (request.amount().signum()<=0 || request.amount().stripTrailingZeros().scale()>0
                || request.amount().compareTo(available)>0
                || request.amount().add(cod.claimed(delivery.deliveryId())).compareTo(delivery.codAmount())>0) conflict();
        UUID id=UUID.randomUUID();
        cod.insertClaim(id,request,order.orderId(),order.customerId(),actor,delivery.deliveryCode(),delivery.codAmount(),hash);
        return cod.find(id);
    }

    @Transactional
    public Collection review(UUID id, Review request, boolean accept) {
        requireRole("ACCOUNTANT");
        Collection old=cod.find(id);
        var order=lockOrder(old.orderId());
        old=cod.find(id); // first read is scalar JDBC, so it cannot poison JPA's locked state
        if (actor().equals(old.shipperId())) denied();
        String target=accept ? "ACCEPTED" : "REJECTED";
        if (target.equals(old.reviewStatus())) return old;
        if (!"SUBMITTED".equals(old.reviewStatus()) || old.version()!=request.expectedVersion()) conflict();
        if (!accept && (request.reason()==null || request.reason().isBlank())) invalid();
        if (accept) {
            // Lock order before delivery, matching claim; a changed source requires reconciliation.
            var delivery=deliveries.lock(old.deliveryId()).orElseThrow(CodService::notFound);
            requireCodDelivery(delivery);
            if (!old.orderId().equals(delivery.orderId()) || !old.customerId().equals(order.customerId())
                    || delivery.codAmount().compareTo(old.expectedAmount())!=0) conflict();
            // Reassignment does not transfer money already collected by the original shipper.
            if (cod.held(old.orderId()).add(old.amount()).compareTo(remaining(order))>0) conflict();
        }
        cod.review(id,target,actor(),accept ? null : request.reason().trim());
        return cod.find(id);
    }

    @Transactional
    public Receipt remit(Remit request) {
        requireRole("ACCOUNTANT");
        UUID actor=actor();
        if (actor.equals(request.shipperId())) denied();
        List<Allocation> items=request.items().stream().sorted(Comparator.comparing(i->i.collectionId().toString())).toList();
        if (new HashSet<>(items.stream().map(Allocation::collectionId).toList()).size()!=items.size()
                || items.stream().map(Allocation::amount).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(request.amount())!=0) invalid();
        String hash=hash(new Remit(request.idempotencyKey(),request.shipperId(),request.amount(),items,request.note()));
        locks.lockPaymentKey("cod-remit:"+request.idempotencyKey());
        UUID replay=cod.receiptByKey(request.idempotencyKey(),hash,actor);
        if (replay!=null) return cod.receipt(replay);
        List<UUID> orderIds=items.stream().map(i->cod.find(i.collectionId()).orderId()).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID,SalesOrderPaymentInfoDto> orders=new HashMap<>();
        Map<UUID,BigDecimal> verified=new HashMap<>();
        for (UUID id:orderIds) {
            var order=lockOrder(id);
            orders.put(id,order);
            verified.put(id,verified(order));
        }
        UUID receipt=UUID.randomUUID();
        cod.insertReceipt(receipt,request,actor,hash);
        for (Allocation line:items) {
            Collection c=cod.find(line.collectionId());
            if (!request.shipperId().equals(c.shipperId()) || actor.equals(c.shipperId())) denied();
            if (!"ACCEPTED".equals(c.reviewStatus()) || c.version()!=line.expectedVersion()
                    || line.amount().signum()<=0 || line.amount().compareTo(c.heldAmount())>0) conflict();
            var order=orders.get(c.orderId());
            BigDecimal paid=verified.get(c.orderId()).add(line.amount());
            if (paid.compareTo(order.totalAmount())>0) conflict();
            UUID paymentId=UUID.randomUUID();
            OffsetDateTime now=OffsetDateTime.now();
            transactions.saveAndFlush(Transaction.builder().transactionId(paymentId).transactionCode("COD-"+paymentId)
                    .transactionType("RECEIPT").paymentType("COD").category("CUSTOMER_PAYMENT")
                    .customerId(order.customerId()).orderId(order.orderId()).invoiceId(order.storeInvoiceId())
                    .amount(line.amount()).paymentMethod("CASH").status("VERIFIED")
                    .description("COD remittance "+receipt).transactionDate(now).createdAt(now).createdBy(actor)
                    .approvedBy(actor).approvedAt(now).build());
            cod.allocate(receipt,c.collectionId(),paymentId,line.amount());
            verified.put(order.orderId(),paid);
        }
        orders.forEach((id,order)->sales.updatePaymentStatus(id,
                verified.get(id).compareTo(order.totalAmount())==0 ? "PAID" : "PARTIALLY_PAID"));
        return cod.receipt(receipt);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<Collection> list(UUID shipper, UUID order, String status, int page, int size) {
        requireViewer();
        if (!staff()) {
            if (shipper!=null && !actor().equals(shipper)) denied();
            shipper=actor();
        }
        if (page<0 || size<1 || size>100 || (status!=null && !List.of("SUBMITTED","ACCEPTED","REJECTED").contains(status))) invalid();
        return cod.list(shipper,order,status,page,size);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Collection detail(UUID id) {
        requireViewer();
        Collection c=cod.find(id);
        if (!staff() && !actor().equals(c.shipperId())) denied();
        return c;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Receipt> history(UUID id) { detail(id); return cod.receipts(id); }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Balance balance(UUID orderId) {
        if (!staff()) denied();
        var order=sales.getOrderPaymentInfo(orderId).orElseThrow(CodService::notFound);
        BigDecimal remaining=remaining(order), held=cod.held(orderId);
        return new Balance(orderId,remaining,held,remaining.subtract(held).max(BigDecimal.ZERO),cod.pending(orderId));
    }

    private SalesOrderPaymentInfoDto lockOrder(UUID id) {
        var order=sales.lockOrderPaymentInfo(id).orElseThrow(CodService::notFound);
        if (!order.validForPayment() || order.totalAmount()==null || order.totalAmount().signum()<=0) conflict();
        return order;
    }
    private static void requireCodDelivery(DeliveryCodSnapshot delivery) {
        if (!"DELIVERED".equals(delivery.status()) || !delivery.codRequired()
                || delivery.codAmount()==null || delivery.codAmount().signum()<=0) conflict();
    }
    private BigDecimal remaining(SalesOrderPaymentInfoDto order) { return order.totalAmount().subtract(verified(order)).max(BigDecimal.ZERO); }
    private BigDecimal verified(SalesOrderPaymentInfoDto order) {
        var payments=transactions.findByOrderIdAndTransactionTypeIgnoreCase(order.orderId(),"RECEIPT");
        if (payments.stream().anyMatch(t->!Objects.equals(order.customerId(),t.getCustomerId()))) conflict();
        return payments.stream().filter(t->"VERIFIED".equalsIgnoreCase(t.getStatus())).map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private UUID actor() { return Objects.requireNonNull(iam.getCurrentUserId(),"Authenticated actor required"); }
    private static boolean role(String role) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getAuthorities().stream().anyMatch(a->("ROLE_"+role).equals(a.getAuthority()));
    }
    private static boolean staff() { return role("ACCOUNTANT") || role("SALES_STAFF") || role("SHOP_OWNER"); }
    private static void requireRole(String role) { if (!role(role)) denied(); }
    private static void requireViewer() { if (!staff() && !role("SHIPPER")) denied(); }
    private String hash(Object value) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | IOException e) { throw new IllegalStateException(e); }
    }
    private static AppException notFound() { return new AppException(CommonErrorCode.RESOURCE_NOT_FOUND); }
    private static void invalid() { throw new AppException(CodErrorCode.INVALID); }
    private static void conflict() { throw new AppException(CodErrorCode.CONFLICT); }
    private static void denied() { throw new AppException(CommonErrorCode.ACCESS_DENIED); }
}
