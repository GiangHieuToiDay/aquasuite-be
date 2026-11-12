package com.aquasuite.ims.module.crm.internal.repository;

import com.aquasuite.ims.module.crm.internal.entity.CustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerCreditProfileRequestRepository extends JpaRepository<CustomerCreditProfileRequest, UUID> {

    boolean existsByCustomerIdAndStatus(UUID customerId, CustomerCreditProfileRequestStatus status);

    Page<CustomerCreditProfileRequest> findByStatus(CustomerCreditProfileRequestStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomerCreditProfileRequest r where r.requestId = :requestId")
    Optional<CustomerCreditProfileRequest> findByIdForUpdate(@Param("requestId") UUID requestId);
}
