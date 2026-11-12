package com.aquasuite.ims.module.inventory.internal.repository;

import com.aquasuite.ims.module.inventory.internal.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
    List<GoodsReceipt> findByPurchaseOrderId(UUID purchaseOrderId);
    boolean existsByPurchaseOrderIdAndStatus(UUID purchaseOrderId, String status);
    boolean existsByPurchaseOrderIdAndStatusNot(UUID purchaseOrderId, String status);
    long countByPurchaseOrderIdAndStatus(UUID purchaseOrderId, String status);

    /** Danh sách phiếu nhập của một đơn mua hàng, mới nhất trước. */
    List<GoodsReceipt> findByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);

    /** Danh sách phiếu nhập phân trang, mới nhất trước. */
    Page<GoodsReceipt> findAllByOrderByCreatedAtDesc(Pageable pageable);
}