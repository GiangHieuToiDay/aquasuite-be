package com.aquasuite.ims.module.inventory.internal.repository;

import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByVariantIdOrderByCreatedAtDesc(UUID variantId, Pageable pageable);

    List<StockMovement> findTop50ByVariantIdOrderByCreatedAtDesc(UUID variantId);

    Page<StockMovement> findByReferenceIdOrderByCreatedAtDesc(String referenceId, Pageable pageable);
}
