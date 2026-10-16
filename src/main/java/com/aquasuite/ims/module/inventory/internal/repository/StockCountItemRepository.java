package com.aquasuite.ims.module.inventory.internal.repository;

import com.aquasuite.ims.module.inventory.internal.entity.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockCountItemRepository extends JpaRepository<StockCountItem, UUID> {

    List<StockCountItem> findByStockCountId(UUID stockCountId);

    void deleteByStockCountId(UUID stockCountId);
}
