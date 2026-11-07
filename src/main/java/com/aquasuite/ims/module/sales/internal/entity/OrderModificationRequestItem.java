package com.aquasuite.ims.module.sales.internal.entity;

import com.aquasuite.ims.module.sales.internal.enums.OrderModificationItemAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_modification_request_items", indexes = {
        @Index(name = "idx_order_modification_request_items_request_id", columnList = "modification_request_id"),
        @Index(name = "idx_order_modification_request_items_order_item_id", columnList = "order_item_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderModificationRequestItem {

    @Id
    @Column(name = "modification_request_item_id", nullable = false, updatable = false)
    private UUID modificationRequestItemId;

    @Column(name = "modification_request_id", nullable = false, updatable = false)
    private UUID modificationRequestId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "original_variant_id", nullable = false, updatable = false)
    private UUID originalVariantId;

    @Column(name = "requested_variant_id", updatable = false)
    private UUID requestedVariantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_action", nullable = false, updatable = false, length = 16)
    private OrderModificationItemAction changeAction;

    @Column(name = "original_quantity", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal originalQuantity;

    @Column(name = "requested_quantity", updatable = false, precision = 12, scale = 2)
    private BigDecimal requestedQuantity;

    @Column(name = "unit_price_snapshot", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (modificationRequestItemId == null) {
            modificationRequestItemId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
