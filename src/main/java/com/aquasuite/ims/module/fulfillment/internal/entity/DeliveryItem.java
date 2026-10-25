package com.aquasuite.ims.module.fulfillment.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_items",
        indexes = {
                @Index(name = "idx_delivery_items_delivery_id", columnList = "delivery_id"),
                @Index(name = "idx_delivery_items_order_item_id", columnList = "order_item_id"),
                @Index(name = "idx_delivery_items_inventory_id", columnList = "inventory_id"),
                @Index(name = "idx_delivery_items_variant_id", columnList = "variant_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryItem {

    @Id
    @Column(name = "delivery_item_id", nullable = false, updatable = false)
    private UUID deliveryItemId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "serial_numbers", columnDefinition = "jsonb")
    private List<String> serialNumbers;

    @PrePersist
    public void prePersist() {
        if (this.deliveryItemId == null) {
            this.deliveryItemId = UUID.randomUUID();
        }
    }
}
