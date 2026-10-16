package com.aquasuite.ims.module.inventory.internal.entity;

import com.aquasuite.ims.module.inventory.internal.entity.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_movement_variant_created", columnList = "variant_id, created_at DESC"),
        @Index(name = "idx_movement_inventory_id", columnList = "inventory_id"),
        @Index(name = "idx_movement_reference", columnList = "reference_type, reference_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @Column(name = "movement_id", updatable = false, nullable = false)
    private UUID movementId;

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity; // Dương (+) nếu nhập, Âm (-) nếu xuất

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter; // Số dư on_hand_qty ngay sau giao dịch này

    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType; // 'EXCEL_IMPORT', 'GOODS_RECEIPT', 'STOCK_DISPATCH', 'STOCK_COUNT'

    @Column(name = "reference_id", length = 100)
    private String referenceId; // Lưu jobId hoặc UUID chứng từ gốc

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private Long createdBy; // Tham chiếu User.id từ module IAM

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.movementId == null) {
            this.movementId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
