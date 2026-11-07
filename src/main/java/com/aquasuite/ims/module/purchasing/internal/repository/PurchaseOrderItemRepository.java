package com.aquasuite.ims.module.purchasing.internal.repository;

import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderItem;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    List<PurchaseOrderItem> findByPurchaseOrderPurchaseOrderId(UUID purchaseOrderId);

    @Query("SELECT poi FROM PurchaseOrderItem poi WHERE poi.variantId IN :variantIds AND poi.purchaseOrder.status IN :statuses")
    List<PurchaseOrderItem> findOpenItemsByVariantIds(
            @Param("variantIds") Collection<UUID> variantIds,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses
    );

    @Query("SELECT poi FROM PurchaseOrderItem poi WHERE poi.variantId = :variantId AND poi.purchaseOrder.status IN :statuses")
    List<PurchaseOrderItem> findOpenItemsByVariantId(
            @Param("variantId") UUID variantId,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses
    );

    long countByVariantId(UUID variantId);
}