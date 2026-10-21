package com.aquasuite.ims.module.catalog.internal.entity;

import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "variant_id", updatable = false, nullable = false)
    private UUID variantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", unique = true, nullable = false, length = 100)
    private String sku;

    @Column(name = "variant_name", nullable = false, length = 255)
    private String variantName;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Builder.Default
    @Column(name = "conversion_rate", precision = 12, scale = 4, nullable = false)
    private BigDecimal conversionRate = BigDecimal.ONE;

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "size", length = 100)
    private String size;

    @Builder.Default
    @Column(name = "weight", precision = 10, scale = 3, nullable = false)
    private BigDecimal weight = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technical_spec", columnDefinition = "jsonb")
    private Map<String, Object> technicalSpec;

    @Builder.Default
    @Column(name = "cost_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "selling_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal sellingPrice;

    @Column(name = "warranty_policy_id")
    private UUID warrantyPolicyId;

    @Builder.Default
    @Column(name = "serial_tracking", nullable = false)
    private Boolean serialTracking = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private VariantStatus status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (conversionRate == null) {
            conversionRate = BigDecimal.ONE;
        }
        if (weight == null) {
            weight = BigDecimal.ZERO;
        }
        if (costPrice == null) {
            costPrice = BigDecimal.ZERO;
        }
        if (serialTracking == null) {
            serialTracking = false;
        }
        if (status == null) {
            status = VariantStatus.ACTIVE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
