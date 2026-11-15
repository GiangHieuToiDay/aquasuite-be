package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.WarrantyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarrantyPolicyRepository extends JpaRepository<WarrantyPolicy, UUID> {

    Optional<WarrantyPolicy> findByPolicyCodeIgnoreCase(String policyCode);

    List<WarrantyPolicy> findByStatusOrderByWarrantyMonthsAscPolicyNameAsc(String status);
}

