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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "technical_jobs",
        indexes = {
                @Index(name = "idx_technical_jobs_customer_id", columnList = "customer_id"),
                @Index(name = "idx_technical_jobs_order_id", columnList = "order_id"),
                @Index(name = "idx_technical_jobs_warranty_record_id", columnList = "warranty_record_id"),
                @Index(name = "idx_technical_jobs_technician_id", columnList = "technician_id"),
                @Index(name = "idx_technical_jobs_job_type", columnList = "job_type"),
                @Index(name = "idx_technical_jobs_status", columnList = "status"),
                @Index(name = "idx_technical_jobs_scheduled_at", columnList = "scheduled_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalJob {

    @Id
    @Column(name = "technical_job_id", nullable = false, updatable = false)
    private UUID technicalJobId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "warranty_record_id")
    private UUID warrantyRecordId;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType;

    @Column(name = "service_address", nullable = false, columnDefinition = "TEXT")
    private String serviceAddress;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "RECEIVED";

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "technical_note", columnDefinition = "TEXT")
    private String technicalNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.technicalJobId == null) {
            this.technicalJobId = UUID.randomUUID();
        }
        if (this.status == null) {
            this.status = "RECEIVED";
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
