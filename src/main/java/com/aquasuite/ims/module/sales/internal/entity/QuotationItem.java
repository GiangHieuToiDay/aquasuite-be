package com.aquasuite.ims.module.sales.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

@Entity
@Table(
        name = "quotation_items",
        schema = "public",
        indexes = {
                @Index(name = "idx_quotation_items_quotation_id", columnList = "quotation_id"),
                @Index(name = "idx_quotation_items_parent_item_id", columnList = "parent_item_id"),
                @Index(name = "idx_quotation_items_variant_id", columnList = "variant_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationItem {

    @Id
    @Column(name = "quotation_item_id", nullable = false, updatable = false)
    private UUID quotationItemId;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    @Column(name = "parent_item_id")
    private UUID parentItemId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Builder.Default
    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "backorder_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal backorderQty = BigDecimal.ZERO;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @PrePersist
    public void prePersist() {
        if (this.quotationItemId == null) {
            this.quotationItemId = UUID.randomUUID();
        }
        if (this.discountRate == null) {
            this.discountRate = BigDecimal.ZERO;
        }
        if (this.unitCost == null) {
            this.unitCost = BigDecimal.ZERO;
        }
        if (this.backorderQty == null) {
            this.backorderQty = BigDecimal.ZERO;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}