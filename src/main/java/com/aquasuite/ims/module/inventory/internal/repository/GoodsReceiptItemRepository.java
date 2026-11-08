package com.aquasuite.ims.module.inventory.internal.repository;

import com.aquasuite.ims.module.inventory.internal.entity.GoodsReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository cho các dòng chi tiết của phiếu nhập kho (goods_receipt_items). */
@Repository
public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {

    /** Lấy toàn bộ dòng của một phiếu nhập kho. */
    List<GoodsReceiptItem> findByGoodsReceiptId(UUID goodsReceiptId);
}
