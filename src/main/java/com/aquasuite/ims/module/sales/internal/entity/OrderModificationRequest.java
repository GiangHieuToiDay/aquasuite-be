package com.aquasuite.ims.module.sales.internal.entity;

import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_modification_requests", indexes = {
        @Index(name = "idx_order_modification_requests_order_id", columnList = "order_id"),
        @Index(name = "idx_order_modification_requests_customer_id", columnList = "customer_id"),
        @Index(name = "idx_order_modification_requests_status", columnList = "request_status"),
        @Index(name = "idx_order_modification_requests_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderModificationRequest {

    @Id
    @Column(name = "modification_request_id", nullable = false, updatable = false)
    private UUID modificationRequestId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, updatable = false, length = 32)
    private OrderModificationRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 32)
    private OrderModificationRequestStatus requestStatus;

    @Column(name = "customer_reason", nullable = false, columnDefinition = "TEXT")
    private String customerReason;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "replacement_order_id")
    private UUID replacementOrderId;

    @Column(name = "original_invoice_id")
    private UUID originalInvoiceId;

    @Column(name = "resulting_invoice_id")
    private UUID resultingInvoiceId;

    @Column(name = "tax_result_reference", length = 100)
    private String taxResultReference;

    @Column(name = "price_difference", precision = 15, scale = 2)
    private BigDecimal priceDifference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (modificationRequestId == null) {
            modificationRequestId = UUID.randomUUID();
        }
        if (requestStatus == null) {
            requestStatus = OrderModificationRequestStatus.PENDING_REVIEW;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
