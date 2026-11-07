package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.OrderPaymentProof;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn và lưu chứng từ chuyển khoản của đơn hàng. */
public interface OrderPaymentProofRepository extends JpaRepository<OrderPaymentProof, UUID> {
    /**
     * Lưu chứng từ bằng câu lệnh PostgreSQL chỉ khi giao dịch chưa có chứng từ.
     * @param paymentId mã giao dịch
     * @param mimeType loại nội dung của tệp
     * @param content nội dung tệp
     * @param uploadedBy mã nhân viên tải lên
     * @return số dòng được lưu: 1 khi thành công, 0 khi chứng từ đã tồn tại
     */
    @Modifying
    @Query(value = "INSERT INTO order_payment_proofs(payment_id, mime_type, content, uploaded_by) "
            + "VALUES (:paymentId, :mimeType, :content, :uploadedBy) ON CONFLICT (payment_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("paymentId") UUID paymentId, @Param("mimeType") String mimeType,
            @Param("content") byte[] content, @Param("uploadedBy") UUID uploadedBy);

    /**
     * Tìm chứng từ của giao dịch thu tiền thuộc đúng đơn.
     * @param orderId mã đơn sở hữu giao dịch
     * @param paymentId mã giao dịch
     * @return chứng từ phù hợp nếu tồn tại
     */
    @Query("SELECT p FROM OrderPaymentProof p, Transaction t WHERE p.paymentId = t.transactionId "
            + "AND t.orderId = :orderId AND t.transactionId = :paymentId AND t.transactionType = 'RECEIPT'")
    Optional<OrderPaymentProof> findByOrderAndPayment(@Param("orderId") UUID orderId,
            @Param("paymentId") UUID paymentId);
}
