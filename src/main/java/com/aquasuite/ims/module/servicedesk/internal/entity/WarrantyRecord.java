package com.aquasuite.ims.module.servicedesk.internal.entity;

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

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "warranty_records",
        indexes = {
                @Index(name = "idx_warranty_records_customer_id", columnList = "customer_id"),
                @Index(name = "idx_warranty_records_order_item_id", columnList = "order_item_id"),
                @Index(name = "idx_warranty_records_variant_id", columnList = "variant_id"),
                @Index(name = "idx_warranty_records_serial_number", columnList = "serial_number"),
                @Index(name = "idx_warranty_records_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyRecord {

    @Id
    @Column(name = "warranty_record_id", nullable = false, updatable = false)
    private UUID warrantyRecordId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "serial_number", nullable = false, length = 255)
    private String serialNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "warranty_terms", columnDefinition = "TEXT")
    private String warrantyTerms;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @PrePersist
    public void prePersist() {
        if (this.warrantyRecordId == null) {
            this.warrantyRecordId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }
}
