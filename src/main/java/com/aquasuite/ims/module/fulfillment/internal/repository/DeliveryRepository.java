package com.aquasuite.ims.module.fulfillment.internal.repository;

import com.aquasuite.ims.module.fulfillment.internal.entity.Delivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.deliveryId = :id")
    Optional<Delivery> lockById(@Param("id") UUID id);
}
