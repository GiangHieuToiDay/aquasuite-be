package com.aquasuite.ims.module.crm.internal.repository;

import com.aquasuite.ims.module.crm.internal.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescUpdatedAtDesc(UUID customerId);

    Optional<CustomerAddress> findByAddressIdAndCustomerIdAndIsDeletedFalse(UUID addressId, UUID customerId);

    Optional<CustomerAddress> findByAddressIdAndIsDeletedFalse(UUID addressId);

    Optional<CustomerAddress> findFirstByCustomerIdAndIsDeletedFalseOrderByUpdatedAtDesc(UUID customerId);

    Optional<CustomerAddress> findByCustomerIdAndIsDefaultTrueAndIsDeletedFalse(UUID customerId);

    long countByCustomerIdAndIsDeletedFalse(UUID customerId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.isDefault = false, a.updatedAt = CURRENT_TIMESTAMP WHERE a.customerId = :customerId")
    void clearDefaultAddresses(@Param("customerId") UUID customerId);
}
