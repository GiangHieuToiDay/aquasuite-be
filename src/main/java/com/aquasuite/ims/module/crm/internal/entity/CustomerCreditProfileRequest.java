package com.aquasuite.ims.module.crm.internal.entity;

import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_credit_profile_requests", schema = "public", indexes = {
        @Index(name = "idx_customer_credit_profile_request_status_created", columnList = "status,created_at"),
        @Index(name = "idx_customer_credit_profile_request_customer_created", columnList = "customer_id,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCreditProfileRequest {

    @Id
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "requested_credit_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedCreditLimit;

    @Column(name = "requested_credit_term_days", nullable = false)
    private Integer requestedCreditTermDays;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "identity_document_number", length = 32)
    private String identityDocumentNumber;

    @Column(name = "residential_address", length = 500)
    private String residentialAddress;

    @Column(name = "worksite_address", length = 500)
    private String worksiteAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private CustomerCreditProfileRequestStatus status = CustomerCreditProfileRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (requestId == null) requestId = UUID.randomUUID();
        if (status == null) status = CustomerCreditProfileRequestStatus.PENDING;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
