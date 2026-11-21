package com.aquasuite.ims.entity.sales;

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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Thông tin giao dịch link thanh toán PayOS gắn với một đơn hàng trực tuyến (UC-18).
 */
@Entity
@Table(
        name = "online_payos_payments",
        indexes = {
                @Index(name = "uq_online_payos_order_id", columnList = "order_id", unique = true),
                @Index(name = "uq_online_payos_order_code", columnList = "payos_order_code", unique = true),
                @Index(name = "idx_online_payos_link_id", columnList = "payment_link_id"),
                @Index(name = "idx_online_payos_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePayosPayment {

    @Id
    @Column(name = "payos_payment_id", nullable = false, updatable = false)
    private UUID payosPaymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payos_order_code", nullable = false)
    private Long payosOrderCode;

    @Column(name = "payment_link_id", nullable = false, length = 128)
    private String paymentLinkId;

    @Column(name = "checkout_url", nullable = false, columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(name = "bin", length = 32)
    private String bin;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(name = "account_name", length = 255)
    private String accountName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "amount_paid", nullable = false)
    @Builder.Default
    private Long amountPaid = 0L;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.payosPaymentId == null) {
            this.payosPaymentId = UUID.randomUUID();
        }
        if (this.amountPaid == null) {
            this.amountPaid = 0L;
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING";
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

