package com.aquasuite.ims.entity.finance;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Finance ledger entry that consumes part of a verified receipt after UC-64 verifies a customer refund.
 * It prevents a reversed allocation from returning refunded money to the UC-63 allocation queue.
 */
@Entity
@Table(name = "payment_receipt_refund_settlements", indexes = {
        @Index(name = "idx_payment_receipt_refund_settlements_receipt", columnList = "receipt_transaction_id"),
        @Index(name = "idx_payment_receipt_refund_settlements_refund", columnList = "refund_transaction_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceiptRefundSettlement {

    @Id
    @Column(name = "settlement_id", nullable = false, updatable = false)
    private UUID settlementId;

    @Column(name = "receipt_transaction_id", nullable = false, updatable = false)
    private UUID receiptTransactionId;

    @Column(name = "refund_transaction_id", nullable = false, updatable = false)
    private UUID refundTransactionId;

    @Column(name = "source_order_id", nullable = false, updatable = false)
    private UUID sourceOrderId;

    @Column(name = "settled_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal settledAmount;

    @Column(name = "settled_by", nullable = false, updatable = false)
    private UUID settledBy;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private OffsetDateTime settledAt;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String reason;

    @PrePersist
    public void prePersist() {
        if (settlementId == null) settlementId = UUID.randomUUID();
        if (settledAt == null) settledAt = OffsetDateTime.now();
    }
}
