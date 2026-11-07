package com.aquasuite.ims.module.purchasing.internal.repository;

import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrder;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    Optional<PurchaseOrder> findByPoCode(String poCode);

    boolean existsByPoCode(String poCode);

    Optional<PurchaseOrder> findTopByPoCodeStartingWithOrderByPoCodeDesc(String prefix);

    long countByPoCodeStartingWith(String prefix);

    List<PurchaseOrder> findBySupplierId(UUID supplierId);

    List<PurchaseOrder> findBySupplierIdAndStatusIn(UUID supplierId, Collection<PurchaseOrderStatus> statuses);
}