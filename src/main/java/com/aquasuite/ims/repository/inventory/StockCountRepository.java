package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.StockCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, UUID> {

    Optional<StockCount> findByStockCountCode(String stockCountCode);

    boolean existsByStockCountCode(String stockCountCode);

    Page<StockCount> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
