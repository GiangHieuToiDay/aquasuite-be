package com.aquasuite.ims.module.inventory.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "stock_count_items",
        indexes = {
            @Index(name = "idx_item_stock_count_id", columnList = "stock_count_id"),
            @Index(name = "idx_item_variant_id", columnList = "variant_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stock_count_item_id", updatable = false, nullable = false)
    private UUID stockCountItemId;

    @Column(name = "stock_count_id", nullable = false)
    private UUID stockCountId;

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "system_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal systemQty;

    @Column(name = "actual_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualQty;

    @Column(name = "difference_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal differenceQty;

    @Column(name = "reason", length = 255)
    private String reason;
}
