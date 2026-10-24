package com.aquasuite.ims.module.crm.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_addresses", schema = "public", indexes = {
        @Index(name = "idx_customer_addresses_customer", columnList = "customer_id, is_deleted"),
        @Index(name = "idx_customer_addresses_default", columnList = "customer_id, is_default")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAddress {

    @Id
    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "ward", nullable = false, length = 100)
    private String ward;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Column(name = "address_text", nullable = false, length = 500)
    private String addressText;

    @Column(name = "label", nullable = false, length = 50)
    @Builder.Default
    private String label = "Home"; // Home, Construction Site, Villa, Office, etc.

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.addressId == null) {
            this.addressId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.label == null || this.label.isBlank()) {
            this.label = "Home";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
