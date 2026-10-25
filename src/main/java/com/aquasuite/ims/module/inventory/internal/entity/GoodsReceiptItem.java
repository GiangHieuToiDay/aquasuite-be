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
        name = "goods_receipt_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_goods_receipt_items_receipt_po_item",
                        columnNames = {"goods_receipt_id", "purchase_order_item_id"}
                )
        },
        indexes = {
                @Index(name = "idx_goods_receipt_items_receipt_id", columnList = "goods_receipt_id"),
                @Index(name = "idx_goods_receipt_items_po_item_id", columnList = "purchase_order_item_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceiptItem {

    @Id
    @Column(name = "goods_receipt_item_id", nullable = false, updatable = false)
    private UUID goodsReceiptItemId;

    @Column(name = "goods_receipt_id", nullable = false)
    private UUID goodsReceiptId;

    @Column(name = "purchase_order_item_id", nullable = false)
    private UUID purchaseOrderItemId;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @PrePersist
    public void prePersist() {
        if (this.goodsReceiptItemId == null) {
            this.goodsReceiptItemId = UUID.randomUUID();
        }
    }
}
