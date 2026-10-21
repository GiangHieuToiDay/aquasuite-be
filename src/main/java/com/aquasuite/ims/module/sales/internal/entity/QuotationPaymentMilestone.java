package com.aquasuite.ims.module.sales.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "quotation_payment_milestones", indexes = {
        @Index(name = "idx_quotation_milestones_quotation_id", columnList = "quotation_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationPaymentMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "milestone_id", updatable = false, nullable = false)
    private Long milestoneId;

    @Column(name = "quotation_id", nullable = false)
    private Long quotationId;

    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    @Column(name = "percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @PrePersist
    public void prePersist() {
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}
