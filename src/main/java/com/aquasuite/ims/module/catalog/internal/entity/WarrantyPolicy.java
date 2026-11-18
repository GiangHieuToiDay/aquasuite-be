package com.aquasuite.ims.module.catalog.internal.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chính sách bảo hành chuẩn dùng nội bộ trong module Catalog (POJO đọc/ghi qua JDBC).
 * Bảng {@code public.warranty_policies} chỉ do {@code servicedesk} sở hữu ánh xạ JPA {@code @Entity}
 * nhằm tránh trùng lặp entity mapping trên Hibernate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyPolicy {

    private UUID warrantyPolicyId;

    private String policyCode;

    private String policyName;

    private UUID brandId;

    private UUID categoryId;

    private Integer warrantyMonths;

    private String warrantyTerms;

    @Builder.Default
    private String status = "ACTIVE";

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

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

    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
