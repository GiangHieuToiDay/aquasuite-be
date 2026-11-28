package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.GoodsReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repository cho các dòng chi tiết của phiếu nhập kho (goods_receipt_items). */
@Repository
public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {

    /** Lấy toàn bộ dòng của một phiếu nhập kho. */
    List<GoodsReceiptItem> findByGoodsReceiptId(UUID goodsReceiptId);

    /** Lấy các dòng của một biến thể trên tập phiếu nhập kho. */
    List<GoodsReceiptItem> findByGoodsReceiptIdInAndVariantId(Collection<UUID> goodsReceiptIds, UUID variantId);
}
