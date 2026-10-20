package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findBySku(String sku);

    List<ProductVariant> findBySkuIn(Collection<String> skus);

    List<ProductVariant> findByProductId(UUID productId);

    List<ProductVariant> findByProductIdIn(Collection<UUID> productIds);

    boolean existsBySku(String sku);

    boolean existsBySkuAndVariantIdNot(String sku, UUID variantId);

    @Modifying
    @Query("DELETE FROM ProductVariant pv WHERE pv.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);
}
