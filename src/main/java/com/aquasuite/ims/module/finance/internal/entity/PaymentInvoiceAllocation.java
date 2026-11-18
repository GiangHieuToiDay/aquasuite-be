package com.aquasuite.ims.module.finance.internal.entity;

import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationStatus;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Finance-owned immutable money-allocation row.
 * Cross-module references remain scalar UUIDs; this entity has no JPA associations to Sales.
 */
@Entity
@Table(name = "payment_invoice_allocations", indexes = {
        @Index(name = "idx_payment_invoice_allocations_transaction_active", columnList = "transaction_id"),
        @Index(name = "idx_payment_invoice_allocations_invoice_active", columnList = "invoice_id"),
        @Index(name = "idx_payment_invoice_allocations_order_active", columnList = "order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInvoiceAllocation {

    @Id
    @Column(name = "allocation_id", nullable = false, updatable = false)
    private UUID allocationId;

    @Column(name = "allocation_request_id", nullable = false, updatable = false)
    private UUID allocationRequestId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal allocatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 32, updatable = false)
    @Builder.Default
    private PaymentAllocationType allocationType = PaymentAllocationType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_status", nullable = false, length = 32)
    @Builder.Default
    private PaymentAllocationStatus allocationStatus = PaymentAllocationStatus.ACTIVE;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "allocated_by", nullable = false, updatable = false)
    private UUID allocatedBy;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    private OffsetDateTime allocatedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "reversal_reason", columnDefinition = "TEXT")
    private String reversalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (allocationId == null) allocationId = UUID.randomUUID();
        if (allocationType == null) allocationType = PaymentAllocationType.MANUAL;
        if (allocationStatus == null) allocationStatus = PaymentAllocationStatus.ACTIVE;
        if (allocatedAt == null) allocatedAt = OffsetDateTime.now();
        if (createdAt == null) createdAt = allocatedAt;
    }
}
