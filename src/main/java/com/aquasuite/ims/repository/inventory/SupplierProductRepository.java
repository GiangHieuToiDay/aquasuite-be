package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.SupplierProduct;
import com.aquasuite.ims.entity.inventory.SupplierProductId;
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