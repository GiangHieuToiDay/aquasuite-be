package com.aquasuite.ims.module.purchasing.internal.entity;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderDeliveryType;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
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

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "warehouse_code", length = 50)
    @Builder.Default
    private String warehouseCode = "WH_MAIN";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "close_reason", columnDefinition = "TEXT")
    private String closeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_plan_type", nullable = false, length = 32)
    @Builder.Default
    private PurchaseOrderDeliveryType deliveryPlanType = PurchaseOrderDeliveryType.SINGLE;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "is_short_closed", nullable = false)
    @Builder.Default
    private Boolean isShortClosed = false;

    @Column(name = "reopened_by")
    private UUID reopenedBy;

    @Column(name = "reopened_at")
    private OffsetDateTime reopenedAt;

    @Column(name = "reopen_reason", columnDefinition = "TEXT")
    private String reopenReason;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<PurchaseOrderStage> stages = new ArrayList<>();

    public void addStage(PurchaseOrderStage stage) {
        if (this.stages == null) {
            this.stages = new ArrayList<>();
        }
        stage.setPurchaseOrder(this);
        this.stages.add(stage);
    }

    public void removeStage(PurchaseOrderStage stage) {
        if (this.stages != null) {
            this.stages.remove(stage);
            stage.setPurchaseOrder(null);
        }
    }

    public void clearStages() {
        if (this.stages != null) {
            for (PurchaseOrderStage stage : this.stages) {
                stage.setPurchaseOrder(null);
            }
            this.stages.clear();
        }
    }

    public void addItem(PurchaseOrderItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        item.setPurchaseOrder(this);
        this.items.add(item);
    }

    public void removeItem(PurchaseOrderItem item) {
        if (this.items != null) {
            this.items.remove(item);
            item.setPurchaseOrder(null);
        }
    }

    public void clearItems() {
        if (this.items != null) {
            for (PurchaseOrderItem item : this.items) {
                item.setPurchaseOrder(null);
            }
            this.items.clear();
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.purchaseOrderId == null) {
            this.purchaseOrderId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = PurchaseOrderStatus.DRAFT;
        }
        if (this.totalAmount == null) {
            this.totalAmount = BigDecimal.ZERO;
        }
        if (this.orderDate == null) {
            this.orderDate = LocalDate.now();
        }
        if (this.warehouseCode == null) {
            this.warehouseCode = "WH_MAIN";
        }
        if (this.deliveryPlanType == null) {
            this.deliveryPlanType = PurchaseOrderDeliveryType.SINGLE;
        }
        if (this.paidAmount == null) {
            this.paidAmount = BigDecimal.ZERO;
        }
        if (this.isShortClosed == null) {
            this.isShortClosed = false;
        }
        if (this.version == null) {
            this.version = 0;
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