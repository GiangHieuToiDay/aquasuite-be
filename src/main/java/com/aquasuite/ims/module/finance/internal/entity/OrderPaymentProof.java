package com.aquasuite.ims.module.finance.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chứng từ chuyển khoản được lưu cho một giao dịch thanh toán. */
@Entity
@Table(name = "order_payment_proofs")
@Getter
@NoArgsConstructor
public class OrderPaymentProof {
    /** Mã giao dịch thanh toán; mỗi giao dịch chỉ có một chứng từ. */
    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    /** Loại nội dung được xác định từ chữ ký tệp. */
    @Column(name = "mime_type", nullable = false, length = 32)
    private String mimeType;

    /** Nội dung nhị phân của chứng từ trong PostgreSQL. */
    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    /** Mã nhân viên đã tải chứng từ. */
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /** Thời điểm tải lên do database đặt khi lưu chứng từ. */
    @Column(name = "uploaded_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime uploadedAt;
}
