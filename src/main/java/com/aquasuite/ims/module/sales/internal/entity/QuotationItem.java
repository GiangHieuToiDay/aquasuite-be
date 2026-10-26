package com.aquasuite.ims.module.sales.internal.entity;

import com.aquasuite.ims.module.sales.internal.entity.enums.QuotationItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quotation_items", indexes = {
        @Index(name = "idx_quotation_items_quotation_id", columnList = "quotation_id"),
        @Index(name = "idx_quotation_items_parent_id", columnList = "parent_item_id"),
        @Index(name = "idx_quotation_items_combo_id", columnList = "combo_id"),
        @Index(name = "idx_quotation_items_variant_id", columnList = "variant_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "quotation_item_id", updatable = false, nullable = false)
    private UUID quotationItemId;

    @Column(name = "quotation_id", nullable = false)
    private UUID quotationId;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "item_type", nullable = false, length = 20)
    private QuotationItemType itemType = QuotationItemType.SINGLE;

    @Column(name = "parent_item_id")
    private UUID parentItemId;

    @Column(name = "combo_id")
    private UUID comboId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "sku_snapshot", length = 50)
    private String skuSnapshot;

    @Column(name = "item_name_snapshot", nullable = false, length = 255)
    private String itemNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Builder.Default
    @Column(name = "cost_price_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPriceSnapshot = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Builder.Default
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "net_unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal netUnitPrice;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    @Builder.Default
    @Column(name = "available_stock_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal availableStockQty = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "incoming_stock_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal incomingStockQty = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "backorder_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal backorderQty = BigDecimal.ZERO;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @PrePersist
    public void prePersist() {
        if (this.sortOrder == null) this.sortOrder = 0;
        if (this.itemType == null) this.itemType = QuotationItemType.SINGLE;
        if (this.costPriceSnapshot == null) this.costPriceSnapshot = BigDecimal.ZERO;
        if (this.discountPercent == null) this.discountPercent = BigDecimal.ZERO;
        if (this.discountAmount == null) this.discountAmount = BigDecimal.ZERO;
        if (this.availableStockQty == null) this.availableStockQty = BigDecimal.ZERO;
        if (this.incomingStockQty == null) this.incomingStockQty = BigDecimal.ZERO;
        if (this.backorderQty == null) this.backorderQty = BigDecimal.ZERO;
    }
}
