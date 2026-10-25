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
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "sales_invoices",
        indexes = {
                @Index(name = "idx_sales_invoices_invoice_code", columnList = "invoice_code", unique = true),
                @Index(name = "idx_sales_invoices_order_id", columnList = "order_id"),
                @Index(name = "idx_sales_invoices_customer_id", columnList = "customer_id"),
                @Index(name = "idx_sales_invoices_status", columnList = "status"),
                @Index(name = "idx_sales_invoices_issue_date", columnList = "issue_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoice {

    @Id
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "invoice_code", unique = true, nullable = false, length = 100)
    private String invoiceCode;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_type", nullable = false, length = 32)
    private String invoiceType;

    @Column(name = "issue_date", nullable = false)
    private OffsetDateTime issueDate;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.invoiceId == null) {
            this.invoiceId = UUID.randomUUID();
        }
        if (this.issueDate == null) {
            this.issueDate = OffsetDateTime.now();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.subtotal == null) {
            this.subtotal = BigDecimal.ZERO;
        }
        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }
        if (this.taxAmount == null) {
            this.taxAmount = BigDecimal.ZERO;
        }
        if (this.totalAmount == null) {
            this.totalAmount = BigDecimal.ZERO;
        }
    }
}
