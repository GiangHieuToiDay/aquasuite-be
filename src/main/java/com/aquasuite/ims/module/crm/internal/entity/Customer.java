package com.aquasuite.ims.module.crm.internal.entity;

import com.aquasuite.ims.module.crm.internal.entity.enums.CustomerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers", schema = "public", indexes = {
        @Index(name = "idx_customers_user_id", columnList = "user_id"),
        @Index(name = "idx_customers_phone", columnList = "phone")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /**
     * Tham chiếu Scalar ID sang module IAM (users.id).
     * Tuân thủ quy tắc kiến trúc AquaSuite: Không tạo JPA cross-module entity relation.
     */
    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String customerCode;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 32)
    @Builder.Default
    private CustomerType customerType = CustomerType.RETAIL;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.customerId == null) {
            this.customerId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
        if (this.customerType == null) {
            this.customerType = CustomerType.RETAIL;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
