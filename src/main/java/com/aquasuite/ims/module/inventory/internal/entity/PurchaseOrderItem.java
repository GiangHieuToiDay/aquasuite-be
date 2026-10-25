package com.aquasuite.ims.module.inventory.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    /** Tham chiếu Scalar ID sang module catalog (product_variants.variant_id) */
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "quantity_ordered", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityOrdered;

    @Column(name = "quantity_received", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @PrePersist
    public void prePersist() {
        if (this.purchaseOrderItemId == null) {
            this.purchaseOrderItemId = UUID.randomUUID();
        }
        if (this.quantityReceived == null) {
            this.quantityReceived = BigDecimal.ZERO;
        }
    }
}
