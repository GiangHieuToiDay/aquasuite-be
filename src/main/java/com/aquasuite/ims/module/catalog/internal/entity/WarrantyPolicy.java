package com.aquasuite.ims.module.catalog.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chính sách bảo hành chuẩn của danh mục (Phương án B).
 * Được liên kết từ {@link ProductVariant#getWarrantyPolicyId()} và sử dụng khi cấp thẻ bảo hành UC-45.
 */
@Entity(name = "CatalogWarrantyPolicy")
@Table(name = "warranty_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyPolicy {

    @Id
    @Column(name = "warranty_policy_id", nullable = false, updatable = false)
    private UUID warrantyPolicyId;

    @Column(name = "policy_code", nullable = false, unique = true, length = 50)
    private String policyCode;

    @Column(name = "policy_name", nullable = false, length = 150)
    private String policyName;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "warranty_period_months", nullable = false)
    private Integer warrantyMonths;

    @Column(name = "coverage_conditions", columnDefinition = "TEXT")
    private String warrantyTerms;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.warrantyPolicyId == null) {
            this.warrantyPolicyId = UUID.randomUUID();
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "ACTIVE";
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
}

