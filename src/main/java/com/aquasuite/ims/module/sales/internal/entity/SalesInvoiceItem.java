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
        name = "sales_invoice_items",
        indexes = {
                @Index(name = "idx_sales_invoice_items_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_sales_invoice_items_order_item_id", columnList = "order_item_id"),
                @Index(name = "idx_sales_invoice_items_variant_id", columnList = "variant_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceItem {

    @Id
    @Column(name = "invoice_item_id", nullable = false, updatable = false)
    private UUID invoiceItemId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @PrePersist
    public void prePersist() {
        if (this.invoiceItemId == null) {
            this.invoiceItemId = UUID.randomUUID();
        }
        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }
        if (this.taxRate == null) {
            this.taxRate = BigDecimal.ZERO;
        }
    }
}
