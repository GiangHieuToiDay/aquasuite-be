package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findBySkuIgnoreCase(String sku);

    List<ProductVariant> findBySkuIn(Collection<String> skus);

    List<ProductVariant> findByProductId(UUID productId);

    Page<ProductVariant> findByProductIdAndStatusNot(UUID productId, com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus status, Pageable pageable);

    boolean existsBySkuIgnoreCase(String sku);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status <> 'DELETED' " +
            "AND (:status IS NULL OR v.status = :status) " +
            "AND (:keyword IS NULL " +
            "OR lower(v.sku) LIKE cast(:keyword as String) " +
            "OR lower(v.variantName) LIKE cast(:keyword as String) " +
            "OR lower(coalesce(v.color, '')) LIKE cast(:keyword as String))")
    Page<ProductVariant> search(
            @Param("productId") UUID productId,
            @Param("keyword") String keyword,
            @Param("status") com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus status,
            Pageable pageable);

    long countByProductIdAndStatusNot(UUID productId, com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus status);

    long countByProductIdAndStatus(UUID productId, com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus status);

    long countByProductIdAndStatusNotAndSellingPriceIsNull(UUID productId,
                                                           com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus status);
}
