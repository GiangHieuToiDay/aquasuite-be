package com.aquasuite.ims.module.purchasing.internal.repository;

import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderStageRollbackRequest;
import com.aquasuite.ims.module.purchasing.internal.enums.RollbackRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderStageRollbackRequestRepository extends JpaRepository<PurchaseOrderStageRollbackRequest, UUID> {

    List<PurchaseOrderStageRollbackRequest> findByPurchaseOrderIdOrderByRequestedAtDesc(UUID purchaseOrderId);

    List<PurchaseOrderStageRollbackRequest> findByStage_StageIdOrderByRequestedAtDesc(UUID stageId);

    Optional<PurchaseOrderStageRollbackRequest> findFirstByStage_StageIdAndStatus(UUID stageId, RollbackRequestStatus status);

    boolean existsByStage_StageIdAndStatus(UUID stageId, RollbackRequestStatus status);
}

