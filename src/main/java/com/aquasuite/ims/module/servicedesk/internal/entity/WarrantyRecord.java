package com.aquasuite.ims.module.servicedesk.internal.entity;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyDisplayStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Thẻ bảo hành điện tử (UC-45) thuộc module Servicedesk.
 */
@Entity
@Table(
        name = "warranty_records",
        indexes = {
                @Index(name = "idx_warranty_records_customer_id", columnList = "customer_id"),
                @Index(name = "idx_warranty_records_order_item_id", columnList = "order_item_id"),
                @Index(name = "idx_warranty_records_variant_id", columnList = "variant_id"),
                @Index(name = "idx_warranty_records_serial_number", columnList = "serial_number"),
                @Index(name = "idx_warranty_records_status", columnList = "status"),
                @Index(name = "idx_warranty_records_order_id", columnList = "order_id"),
                @Index(name = "idx_warranty_records_policy_id", columnList = "warranty_policy_id"),
                @Index(name = "idx_warranty_records_end_date", columnList = "end_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyRecord {

    @Id
    @Column(name = "warranty_record_id", nullable = false, updatable = false)
    private UUID warrantyRecordId;

    @Column(name = "warranty_code", nullable = false, unique = true, length = 32)
    private String warrantyCode;

    @Column(name = "warranty_policy_id")
    private UUID warrantyPolicyId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "serial_number", length = 255)
    private String serialNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths;

    @Column(name = "warranty_terms", columnDefinition = "TEXT")
    private String warrantyTerms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private WarrantyStatus status = WarrantyStatus.ACTIVE;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.warrantyRecordId == null) {
            this.warrantyRecordId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = WarrantyStatus.ACTIVE;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Đánh dấu hủy thẻ bảo hành một cách bất biến về lý do và dấu vết thời gian/người hủy. */
    public void voidRecord(String reason, UUID actorId, OffsetDateTime now) {
        this.status = WarrantyStatus.VOIDED;
        this.voidReason = reason;
        this.voidedBy = actorId;
        this.voidedAt = now;
        this.updatedAt = now;
    }

    /** Tính trạng thái hiển thị dựa trên trạng thái lưu trữ, công việc sửa chữa đang mở và ngày hiện tại. */
    public WarrantyDisplayStatus computeDisplayStatus(LocalDate today, boolean hasOpenWarrantyJob) {
        if (this.status == WarrantyStatus.VOIDED) {
            return WarrantyDisplayStatus.VOIDED;
        }
        if (hasOpenWarrantyJob) {
            return WarrantyDisplayStatus.CLAIMED;
        }
        if (this.endDate != null && today.isAfter(this.endDate)) {
            return WarrantyDisplayStatus.EXPIRED;
        }
        return WarrantyDisplayStatus.ACTIVE;
    }

    /** Số ngày bảo hành còn lại (>= 0); trả 0 nếu đã hủy hoặc hết hạn. */
    public long computeRemainingDays(LocalDate today) {
        if (this.status == WarrantyStatus.VOIDED || this.endDate == null || today.isAfter(this.endDate)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(today, this.endDate);
    }
}
