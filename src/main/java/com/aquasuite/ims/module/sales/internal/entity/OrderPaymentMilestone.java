package com.aquasuite.ims.module.sales.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import java.util.UUID;

@Entity
@Table(name = "order_payment_milestones", indexes = {
        @Index(name = "idx_order_milestones_order_id", columnList = "order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "milestone_id", updatable = false, nullable = false)
    private UUID milestoneId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    @Column(name = "percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @Builder.Default
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @PrePersist
    public void prePersist() {
        if (this.milestoneId == null) {
            this.milestoneId = UUID.randomUUID();
        }
        if (this.amount == null) {
            this.amount = BigDecimal.ZERO;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}

