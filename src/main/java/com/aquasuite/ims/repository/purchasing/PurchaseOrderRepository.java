package com.aquasuite.ims.repository.purchasing;

import com.aquasuite.ims.entity.purchasing.PurchaseOrder;
import com.aquasuite.ims.enums.purchasing.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Khóa dòng PO (SELECT ... FOR UPDATE) đến hết transaction. Dùng cho mọi thao tác đổi tiến độ
     * nhận hoặc trạng thái sau xác nhận (nhập kho, hủy, đóng thiếu) để chúng được tuần tự hóa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.purchaseOrderId = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);
}