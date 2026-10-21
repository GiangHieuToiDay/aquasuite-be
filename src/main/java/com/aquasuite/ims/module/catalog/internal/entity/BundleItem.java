package com.aquasuite.ims.module.catalog.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bundle_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BundleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bundle_item_id", updatable = false, nullable = false)
    private UUID bundleItemId;

    @Column(name = "bundle_product_id", nullable = false)
    private UUID bundleProductId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Builder.Default
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (quantity == null || quantity <= 0) {
            quantity = 1;
        }
    }
}
