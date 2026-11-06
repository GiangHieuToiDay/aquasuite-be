package com.aquasuite.ims.module.inventory.internal.repository;

import com.aquasuite.ims.module.inventory.internal.entity.SupplierProduct;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProductId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierProductRepository extends JpaRepository<SupplierProduct, SupplierProductId> {
    List<SupplierProduct> findByIdSupplierId(UUID supplierId);
    Optional<SupplierProduct> findByIdSupplierIdAndIdProductId(UUID supplierId, UUID productId);
}