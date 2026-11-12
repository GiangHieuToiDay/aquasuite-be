package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderModificationRequestRepository extends JpaRepository<OrderModificationRequest, UUID> {

    boolean existsByOrderIdAndRequestStatusIn(UUID orderId, Collection<OrderModificationRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from OrderModificationRequest request where request.modificationRequestId = :requestId")
    Optional<OrderModificationRequest> findByIdForUpdate(@Param("requestId") UUID requestId);
}
