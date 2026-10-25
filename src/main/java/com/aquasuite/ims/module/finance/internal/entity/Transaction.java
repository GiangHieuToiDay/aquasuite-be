package com.aquasuite.ims.module.finance.internal.entity;

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
        name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_code", columnList = "transaction_code", unique = true),
                @Index(name = "idx_transactions_type", columnList = "transaction_type"),
                @Index(name = "idx_transactions_customer_id", columnList = "customer_id"),
                @Index(name = "idx_transactions_supplier_id", columnList = "supplier_id"),
                @Index(name = "idx_transactions_order_id", columnList = "order_id"),
                @Index(name = "idx_transactions_goods_receipt_id", columnList = "goods_receipt_id"),
                @Index(name = "idx_transactions_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_transactions_status", columnList = "status"),
                @Index(name = "idx_transactions_date", columnList = "transaction_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "transaction_code", unique = true, nullable = false, length = 100)
    private String transactionCode;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    @Column(name = "payment_type", length = 32)
    private String paymentType;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "goods_receipt_id")
    private UUID goodsReceiptId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @Column(name = "reference_no", length = 255)
    private String referenceNo;

    @Column(name = "proof_url", columnDefinition = "TEXT")
    private String proofUrl;

    @Column(name = "transaction_date", nullable = false)
    private OffsetDateTime transactionDate;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.transactionId == null) {
            this.transactionId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
        if (this.transactionDate == null) {
            this.transactionDate = OffsetDateTime.now();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
