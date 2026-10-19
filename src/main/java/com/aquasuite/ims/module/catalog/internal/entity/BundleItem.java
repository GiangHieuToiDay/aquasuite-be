package com.aquasuite.ims.module.catalog.internal.entity;

import jakarta.persistence.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "bundle_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bundle_product_id", "variant_id"}))
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
    @NotNull
    @Min(1)
    private Integer quantity = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Bundle quantity must be positive");
        }
    }
}
