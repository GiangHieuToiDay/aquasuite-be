package com.aquasuite.ims.module.inventory.internal.entity;

import com.aquasuite.ims.module.inventory.internal.entity.enums.StockCountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "stock_counts",
        indexes = {
            @Index(name = "idx_stock_count_code", columnList = "stock_count_code", unique = true)
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stock_count_id", updatable = false, nullable = false)
    private UUID stockCountId;

    @Column(name = "stock_count_code", unique = true, nullable = false, length = 50)
    private String stockCountCode;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    @Builder.Default
    private String warehouseCode = "WH_MAIN";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private StockCountStatus status = StockCountStatus.DRAFT;

    @Column(name = "counted_by")
    private UUID countedBy; // User.id

    @Column(name = "approved_by")
    private UUID approvedBy; // User.id

    @Column(name = "count_date")
    private LocalDateTime countDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = StockCountStatus.DRAFT;
        if (this.warehouseCode == null) this.warehouseCode = "WH_MAIN";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
