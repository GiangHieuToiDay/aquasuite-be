package com.aquasuite.ims.module.servicedesk.internal.repository;

import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyRecord;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarrantyRecordRepository extends JpaRepository<WarrantyRecord, UUID>, JpaSpecificationExecutor<WarrantyRecord> {
    List<WarrantyRecord> findByCustomerIdOrderByStartDateDesc(UUID customerId);

    long countByOrderItemIdAndStatus(UUID orderItemId, WarrantyStatus status);

    @Query("select count(w) > 0 from WarrantyRecord w where w.variantId = :variantId "
            + "and upper(trim(w.serialNumber)) = upper(trim(:serialNumber)) and w.status = :status")
    boolean existsActiveByVariantIdAndSerialNumber(@Param("variantId") UUID variantId,
                                                    @Param("serialNumber") String serialNumber,
                                                    @Param("status") WarrantyStatus status);

    Optional<WarrantyRecord> findFirstByVariantIdAndSerialNumberIgnoreCaseAndStatus(
            UUID variantId, String serialNumber, WarrantyStatus status);

    Optional<WarrantyRecord> findFirstByOrderItemIdAndDeliveryIdAndSerialNumberIsNullAndStatus(
            UUID orderItemId, UUID deliveryId, WarrantyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WarrantyRecord w where w.warrantyRecordId = :id")
    Optional<WarrantyRecord> findByIdForUpdate(@Param("id") UUID id);
}
