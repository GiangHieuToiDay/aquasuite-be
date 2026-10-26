package com.aquasuite.ims.module.sales.internal.entity;

import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "quotations",
        schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_quotations_code_revision", columnNames = {"quotation_code", "revision_no"})
        },
        indexes = {
                @Index(name = "idx_quotations_customer_id", columnList = "customer_id"),
                @Index(name = "idx_quotations_status", columnList = "status"),
                @Index(name = "idx_quotations_parent_id", columnList = "parent_quotation_id"),
                @Index(name = "idx_quotations_date", columnList = "quotation_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quotation {

    @Id
    @Column(name = "quotation_id", nullable = false, updatable = false)
    private UUID quotationId;

    @Column(name = "quotation_code", nullable = false, length = 100)
    private String quotationCode;

    @Builder.Default
    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo = 1;

    @Column(name = "parent_quotation_id")
    private UUID parentQuotationId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "delivery_schedule", columnDefinition = "TEXT")
    private String deliverySchedule;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 32)
    private QuotationStatus status = QuotationStatus.DRAFT;

    @Column(name = "quotation_date", nullable = false)
    private LocalDate quotationDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Builder.Default
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.valueOf(10);

    @Builder.Default
    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "margin_percent_at_approval", precision = 5, scale = 2)
    private BigDecimal marginPercentAtApproval;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "sent_to_email", length = 320)
    private String sentToEmail;

    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "signed_file_url", columnDefinition = "TEXT")
    private String signedFileUrl;

    @Column(name = "lost_at")
    private OffsetDateTime lostAt;

    @Column(name = "lost_reason", columnDefinition = "TEXT")
    private String lostReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.quotationId == null) {
            this.quotationId = UUID.randomUUID();
        }
        if (this.revisionNo == null) {
            this.revisionNo = 1;
        }
        if (this.status == null) {
            this.status = QuotationStatus.DRAFT;
        }
        if (this.vatRate == null) {
            this.vatRate = BigDecimal.valueOf(10);
        }
        if (this.shippingFee == null) {
            this.shippingFee = BigDecimal.ZERO;
        }
        if (this.totalAmount == null) {
            this.totalAmount = BigDecimal.ZERO;
        }
        if (this.quotationDate == null) {
            this.quotationDate = LocalDate.now();
        }
        if (this.validUntil == null) {
            this.validUntil = this.quotationDate.plusDays(30);
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