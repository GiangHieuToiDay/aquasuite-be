package com.aquasuite.ims.module.fulfillment.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(
        name = "deliveries",
        indexes = {
                @Index(name = "idx_deliveries_delivery_code", columnList = "delivery_code", unique = true),
                @Index(name = "idx_deliveries_order_id", columnList = "order_id"),
                @Index(name = "idx_deliveries_warehouse_code", columnList = "warehouse_code"),
                @Index(name = "idx_deliveries_shipper_id", columnList = "shipper_id"),
                @Index(name = "idx_deliveries_delivery_status", columnList = "delivery_status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delivery {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "delivery_code", unique = true, nullable = false, length = 100)
    private String deliveryCode;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "shipper_id")
    private UUID shipperId;

    @Column(name = "batch_no")
    private Integer batchNo;

    @Column(name = "delivery_status", nullable = false, length = 32)
    @Builder.Default
    private String deliveryStatus = "DRAFT";

    @Column(name = "dispatch_date")
    private OffsetDateTime dispatchDate;

    @Column(name = "scheduled_date")
    private OffsetDateTime scheduledDate;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 32)
    private String recipientPhone;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "cod_required", nullable = false)
    @Builder.Default
    private Boolean codRequired = false;

    @Column(name = "cod_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.deliveryId == null) {
            this.deliveryId = UUID.randomUUID();
        }
        if (this.deliveryStatus == null) {
            this.deliveryStatus = "DRAFT";
        }
        if (this.codRequired == null) {
            this.codRequired = false;
        }
        if (this.codAmount == null) {
            this.codAmount = BigDecimal.ZERO;
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
