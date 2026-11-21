package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockCountItemRepository extends JpaRepository<StockCountItem, UUID> {

    List<StockCountItem> findByStockCountId(UUID stockCountId);

    void deleteByStockCountId(UUID stockCountId);
    long countByVariantId(UUID variantId);
}
