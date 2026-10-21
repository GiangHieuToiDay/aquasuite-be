package com.aquasuite.ims.module.inventory.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProduct {

    @EmbeddedId private SupplierProductId id;

    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @Column(name = "last_supply_price", precision = 15, scale = 2)
    private BigDecimal lastSupplyPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
