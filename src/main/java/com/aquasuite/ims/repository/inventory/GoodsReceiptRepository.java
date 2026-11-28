package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
    List<GoodsReceipt> findByPurchaseOrderId(UUID purchaseOrderId);
    List<GoodsReceipt> findByPurchaseOrderIdInOrderByCreatedAtDesc(Collection<UUID> purchaseOrderIds);
    List<GoodsReceipt> findByReceiptCodeIn(Collection<String> receiptCodes);
    boolean existsByPurchaseOrderIdAndStatus(UUID purchaseOrderId, String status);
    boolean existsByPurchaseOrderIdAndStatusNot(UUID purchaseOrderId, String status);
    long countByPurchaseOrderIdAndStatus(UUID purchaseOrderId, String status);

    /** Danh sách phiếu nhập của một đơn mua hàng, mới nhất trước. */
    List<GoodsReceipt> findByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);

    /** Danh sách phiếu nhập phân trang, mới nhất trước. */
    Page<GoodsReceipt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Phiếu đã được ghi nhận với khóa chống ghi trùng này (nếu có). */
    Optional<GoodsReceipt> findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Khóa advisory theo khóa chống ghi trùng, giữ đến hết transaction, để hai yêu cầu cùng khóa
     * (kể cả khác PO) được xử lý tuần tự: yêu cầu sau luôn thấy phiếu yêu cầu trước đã commit.
     *
     * @param key khóa đã có tiền tố phân biệt nghiệp vụ
     * @return giá trị cố định sau khi lấy được khóa
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS locked", nativeQuery = true)
    int lockIdempotencyKey(@Param("key") String key);
}