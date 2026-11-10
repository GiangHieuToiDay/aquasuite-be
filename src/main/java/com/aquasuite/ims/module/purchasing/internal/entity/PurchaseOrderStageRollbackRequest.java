package com.aquasuite.ims.module.purchasing.internal.entity;

import com.aquasuite.ims.module.purchasing.internal.enums.RollbackRequestStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "purchase_order_stage_rollback_requests",
        indexes = {
                @Index(name = "idx_posrr_stage_id", columnList = "stage_id"),
                @Index(name = "idx_posrr_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderStageRollbackRequest {

    @Id
    @Column(name = "rollback_id", nullable = false, updatable = false)
    private UUID rollbackId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private PurchaseOrderStage stage;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "request_reason", columnDefinition = "TEXT", nullable = false)
    private String requestReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private RollbackRequestStatus status = RollbackRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.rollbackId == null) {
            this.rollbackId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = RollbackRequestStatus.PENDING;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (this.requestedAt == null) {
            this.requestedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
    }
}

