package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.StockMovement;
import com.aquasuite.ims.enums.inventory.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByVariantIdOrderByCreatedAtDesc(UUID variantId, Pageable pageable);

    Page<StockMovement> findByVariantIdAndMovementTypeInOrderByCreatedAtDesc(
            UUID variantId, Collection<MovementType> movementTypes, Pageable pageable);

    List<StockMovement> findTop50ByVariantIdOrderByCreatedAtDesc(UUID variantId);

    Page<StockMovement> findByReferenceIdOrderByCreatedAtDesc(String referenceId, Pageable pageable);
    long countByVariantId(UUID variantId);
}
