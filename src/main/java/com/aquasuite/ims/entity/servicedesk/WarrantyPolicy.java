package com.aquasuite.ims.entity.servicedesk;

import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Live warranty definition. Issued warranty records retain their own terms snapshot. */
@Entity
@Table(name = "warranty_policies", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyPolicy {

    @Id
    @Column(name = "warranty_policy_id", nullable = false, updatable = false)
    private UUID warrantyPolicyId;

    @Column(name = "policy_code", nullable = false, unique = true, length = 32, updatable = false)
    private String policyCode;

    @Column(name = "policy_name", nullable = false, length = 255)
    private String policyName;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "warranty_period_months", nullable = false)
    private Integer warrantyPeriodMonths;

    @Column(name = "coverage_conditions", nullable = false, columnDefinition = "TEXT")
    private String coverageConditions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WarrantyPolicyStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (warrantyPolicyId == null) warrantyPolicyId = UUID.randomUUID();
        if (status == null) status = WarrantyPolicyStatus.ACTIVE;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
