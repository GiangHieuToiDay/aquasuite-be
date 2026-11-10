package com.aquasuite.ims.module.purchasing.internal.repository;

import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderStage;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderStageRepository extends JpaRepository<PurchaseOrderStage, UUID> {

    List<PurchaseOrderStage> findByPurchaseOrder_PurchaseOrderIdOrderBySortOrderAsc(UUID purchaseOrderId);

    Optional<PurchaseOrderStage> findByStageIdAndPurchaseOrder_PurchaseOrderId(UUID stageId, UUID purchaseOrderId);

    long countByPurchaseOrder_PurchaseOrderIdAndStatus(UUID purchaseOrderId, PurchaseOrderStageStatus status);
}

