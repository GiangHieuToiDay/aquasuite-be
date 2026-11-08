package com.aquasuite.ims.module.purchasing.internal.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "purchase_order_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_po_items_po_variant", columnNames = {"purchase_order_id", "variant_id"})
        },
        indexes = {
                @Index(name = "idx_po_items_po_id", columnList = "purchase_order_id"),
                @Index(name = "idx_po_items_variant_id", columnList = "variant_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItem {

    @Id
    @Column(name = "purchase_order_item_id", nullable = false, updatable = false)
    private UUID purchaseOrderItemId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "quantity_ordered", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityOrdered;

    @Column(name = "quantity_received", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost", precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Transient
    public BigDecimal getRemainingQty() {
        BigDecimal ordered = this.quantityOrdered != null ? this.quantityOrdered : BigDecimal.ZERO;
        BigDecimal received = this.quantityReceived != null ? this.quantityReceived : BigDecimal.ZERO;
        return ordered.subtract(received).max(BigDecimal.ZERO);
    }

    @PrePersist
    public void prePersist() {
        if (this.purchaseOrderItemId == null) {
            this.purchaseOrderItemId = UUID.randomUUID();
        }
        if (this.quantityReceived == null) {
            this.quantityReceived = BigDecimal.ZERO;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}