package com.aquasuite.ims.module.inventory.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "purchase_orders",
        indexes = {
                @Index(name = "idx_po_code", columnList = "po_code", unique = true),
                @Index(name = "idx_po_supplier_id", columnList = "supplier_id"),
                @Index(name = "idx_po_status", columnList = "status"),
                @Index(name = "idx_po_order_date", columnList = "order_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @Column(name = "purchase_order_id", nullable = false, updatable = false)
    private UUID purchaseOrderId;

    @Column(name = "po_code", unique = true, nullable = false, length = 100)
    private String poCode;

    /** Tham chiếu Scalar ID sang module inventory (suppliers.supplier_id) */
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Tham chiếu Scalar ID sang module IAM (users.id) */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** Tham chiếu Scalar ID sang module IAM (users.id) */
    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    /** Tham chiếu Scalar ID sang module IAM (users.id) */
    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    /** Tham chiếu Scalar ID sang module IAM (users.id) */
    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "close_reason", columnDefinition = "TEXT")
    private String closeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.purchaseOrderId == null) {
            this.purchaseOrderId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = "DRAFT";
        }
        if (this.totalAmount == null) {
            this.totalAmount = BigDecimal.ZERO;
        }
        if (this.orderDate == null) {
            this.orderDate = LocalDate.now();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
