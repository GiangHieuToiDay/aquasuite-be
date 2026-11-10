package com.aquasuite.ims.module.purchasing.internal.entity;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStageStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "purchase_order_stages",
        indexes = {
                @Index(name = "idx_po_stages_po_id", columnList = "purchase_order_id"),
                @Index(name = "idx_po_stages_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderStage {

    @Id
    @Column(name = "stage_id", nullable = false, updatable = false)
    private UUID stageId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_delivery_date", nullable = false)
    private LocalDate expectedDeliveryDate;

    @Column(name = "percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private PurchaseOrderStageStatus status = PurchaseOrderStageStatus.PENDING;

    @Column(name = "actual_amount", precision = 15, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    @Column(name = "settlement_note", columnDefinition = "TEXT")
    private String settlementNote;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderStageRollbackRequest> rollbackRequests = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.stageId == null) {
            this.stageId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = PurchaseOrderStageStatus.PENDING;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
        if (this.version == null) {
            this.version = 0;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

