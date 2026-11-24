package com.aquasuite.ims.entity.servicedesk;

import com.aquasuite.ims.enums.servicedesk.PreferredSlot;
import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.exception.AppException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phiếu yêu cầu bảo hành điện tử gửi trực tuyến từ khách hàng (UC-47).
 */
@Entity
@Table(name = "warranty_tickets", schema = "public")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyTicket {

    @Id
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 50)
    private String ticketCode;

    @Column(name = "warranty_record_id", nullable = false)
    private UUID warrantyRecordId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_slot", length = 16)
    private PreferredSlot preferredSlot;

    @Column(name = "site_address", nullable = false, columnDefinition = "TEXT")
    private String siteAddress;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<TicketMedia> media = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private WarrantyTicketStatus status = WarrantyTicketStatus.NEW;

    /** Khách đã chủ động xác nhận vẫn gửi yêu cầu dù thẻ bảo hành đã hết hạn (C+). */
    @Column(name = "expired_acknowledged", nullable = false)
    @Builder.Default
    private boolean expiredAcknowledged = false;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.ticketId == null) {
            this.ticketId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = WarrantyTicketStatus.NEW;
        }
        if (this.media == null) {
            this.media = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Factory khởi tạo yêu cầu bảo hành mới gửi từ khách hàng.
     */
    public static WarrantyTicket submit(
            String ticketCode,
            UUID warrantyRecordId,
            UUID customerId,
            String issueDescription,
            LocalDate preferredDate,
            PreferredSlot preferredSlot,
            String siteAddress,
            String contactName,
            String contactPhone,
            String contactEmail,
            List<TicketMedia> mediaList,
            boolean expiredAcknowledged
    ) {
        return WarrantyTicket.builder()
                .ticketId(UUID.randomUUID())
                .ticketCode(ticketCode)
                .warrantyRecordId(warrantyRecordId)
                .customerId(customerId)
                .issueDescription(issueDescription)
                .preferredDate(preferredDate)
                .preferredSlot(preferredSlot)
                .siteAddress(siteAddress)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .contactEmail(contactEmail)
                .media(mediaList != null ? new ArrayList<>(mediaList) : new ArrayList<>())
                .status(WarrantyTicketStatus.NEW)
                .expiredAcknowledged(expiredAcknowledged)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Khách hàng hủy yêu cầu bảo hành (chỉ cho phép khi ở trạng thái NEW).
     */
    public void cancel(String reason, OffsetDateTime now) {
        if (this.status != WarrantyTicketStatus.NEW) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_CANCELLABLE);
        }
        this.status = WarrantyTicketStatus.CANCELLED;
        this.cancelReason = reason != null && !reason.isBlank() ? reason.trim() : "Khách hàng hủy yêu cầu";
        this.updatedAt = now != null ? now : OffsetDateTime.now();
    }

    /**
     * Chủ cửa hàng từ chối yêu cầu bảo hành tại bàn (chỉ khi NEW) — ví dụ thẻ hết hạn, yêu cầu rác/ngoài phạm vi.
     * Nếu chỉ phát hiện không đủ điều kiện khi đang xử lý, Chủ cửa hàng hoàn tất (DONE) và ghi kết quả "Không bảo hành vì ...".
     */
    public void reject(String reason, OffsetDateTime now) {
        if (this.status != WarrantyTicketStatus.NEW) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_REJECTABLE, this.status.name());
        }
        this.status = WarrantyTicketStatus.REJECTED;
        this.rejectionReason = reason.trim();
        this.updatedAt = now != null ? now : OffsetDateTime.now();
    }

    /**
     * Chủ cửa hàng tiếp nhận xử lý yêu cầu bảo hành: NEW → IN_PROGRESS. Chỉ cho phép khi ticket đang chờ tiếp nhận.
     */
    public void startProcessing(OffsetDateTime now) {
        if (this.status != WarrantyTicketStatus.NEW) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_STARTABLE, this.status.name());
        }
        this.status = WarrantyTicketStatus.IN_PROGRESS;
        this.updatedAt = now != null ? now : OffsetDateTime.now();
    }

    /**
     * Chủ cửa hàng hoàn tất yêu cầu bảo hành: IN_PROGRESS → DONE, lưu kết quả xử lý (đã sửa miễn phí, sửa có phí,
     * hoặc không bảo hành vì ... khi phát hiện trong lúc xử lý) và thời điểm hoàn tất.
     */
    public void complete(String result, OffsetDateTime now) {
        if (this.status != WarrantyTicketStatus.IN_PROGRESS) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_TICKET_NOT_COMPLETABLE, this.status.name());
        }
        OffsetDateTime at = now != null ? now : OffsetDateTime.now();
        this.status = WarrantyTicketStatus.DONE;
        this.result = result.trim();
        this.completedAt = at;
        this.updatedAt = at;
    }
}

