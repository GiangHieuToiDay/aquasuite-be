package com.aquasuite.ims.module.sales.internal.entity;

import com.aquasuite.ims.module.sales.internal.entity.enums.QuotationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "quotations", indexes = {
        @Index(name = "idx_quotations_code", columnList = "quotation_code", unique = true),
        @Index(name = "idx_quotations_customer_id", columnList = "customer_id"),
        @Index(name = "idx_quotations_status", columnList = "status"),
        @Index(name = "idx_quotations_parent_id", columnList = "parent_quotation_id"),
        @Index(name = "idx_quotations_created_by", columnList = "created_by")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quotation_id", updatable = false, nullable = false)
    private Long quotationId;

    @Column(name = "quotation_code", unique = true, nullable = false, length = 50)
    private String quotationCode;

    @Builder.Default
    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber = 1;

    @Column(name = "parent_quotation_id")
    private Long parentQuotationId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "quotation_date", nullable = false)
    private LocalDate quotationDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Builder.Default
    @Column(name = "subtotal", nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.valueOf(10);

    @Builder.Default
    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_over_discount_limit", nullable = false)
    private Boolean isOverDiscountLimit = false;

    @Column(name = "estimated_margin_pct", precision = 5, scale = 2)
    private BigDecimal estimatedMarginPct;

    @Column(name = "delivery_schedule", columnDefinition = "TEXT")
    private String deliverySchedule;

    @Column(name = "pdf_file_url", length = 500)
    private String pdfFileUrl;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_to_email", length = 150)
    private String sentToEmail;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "signed_file_url", length = 500)
    private String signedFileUrl;

    @Column(name = "lost_reason", length = 255)
    private String lostReason;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.revisionNumber == null) {
            this.revisionNumber = 1;
        }
        if (this.status == null) {
            this.status = QuotationStatus.DRAFT;
        }
        if (this.subtotal == null) this.subtotal = BigDecimal.ZERO;
        if (this.discountAmount == null) this.discountAmount = BigDecimal.ZERO;
        if (this.netAmount == null) this.netAmount = BigDecimal.ZERO;
        if (this.taxRate == null) this.taxRate = BigDecimal.valueOf(10);
        if (this.taxAmount == null) this.taxAmount = BigDecimal.ZERO;
        if (this.shippingFee == null) this.shippingFee = BigDecimal.ZERO;
        if (this.totalAmount == null) this.totalAmount = BigDecimal.ZERO;
        if (this.isOverDiscountLimit == null) this.isOverDiscountLimit = false;
        if (this.quotationDate == null) {
            this.quotationDate = LocalDate.now();
        }
        if (this.validUntil == null) {
            this.validUntil = this.quotationDate.plusDays(30);
        }
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
