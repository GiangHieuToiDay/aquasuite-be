package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Optional<Supplier> findBySupplierCode(String supplierCode);

    boolean existsBySupplierCode(String supplierCode);
}
