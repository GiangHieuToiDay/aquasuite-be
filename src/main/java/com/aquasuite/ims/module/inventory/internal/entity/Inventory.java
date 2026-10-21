package com.aquasuite.ims.module.inventory.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "inventories",
        indexes = {
            @Index(name = "idx_inv_variant_id", columnList = "variant_id"),
            @Index(
                    name = "idx_inv_variant_wh",
                    columnList = "variant_id, warehouse_code",
                    unique = true)
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column(name = "inventory_id", updatable = false, nullable = false)
    private UUID inventoryId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    @Builder.Default
    private String warehouseCode = "WH_MAIN";

    @Column(name = "on_hand_qty", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal onHandQty = BigDecimal.ZERO;

    @Column(name = "reserved_qty", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal reservedQty = BigDecimal.ZERO;

    @Column(name = "minimum_qty", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minimumQty = BigDecimal.ZERO;

    @Column(name = "maximum_qty", precision = 12, scale = 2)
    private BigDecimal maximumQty;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Tồn khả dụng: available_qty = on_hand_qty - reserved_qty */
    @Transient
    public BigDecimal getAvailableQty() {
        BigDecimal onHand = this.onHandQty != null ? this.onHandQty : BigDecimal.ZERO;
        BigDecimal reserved = this.reservedQty != null ? this.reservedQty : BigDecimal.ZERO;
        return onHand.subtract(reserved);
    }

    /** Kiểm tra cảnh báo sắp hết hàng: chỉ cảnh báo nếu có cài minimumQty > 0 */
    public boolean isLowStock() {
        return this.minimumQty != null
                && this.minimumQty.compareTo(BigDecimal.ZERO) > 0
                && getAvailableQty().compareTo(this.minimumQty) <= 0;
    }

    @PrePersist
    public void prePersist() {
        if (this.inventoryId == null) {
            this.inventoryId = UUID.randomUUID();
        }
        if (this.warehouseCode == null) {
            this.warehouseCode = "WH_MAIN";
        }
        if (this.onHandQty == null) this.onHandQty = BigDecimal.ZERO;
        if (this.reservedQty == null) this.reservedQty = BigDecimal.ZERO;
        if (this.minimumQty == null) this.minimumQty = BigDecimal.ZERO;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
