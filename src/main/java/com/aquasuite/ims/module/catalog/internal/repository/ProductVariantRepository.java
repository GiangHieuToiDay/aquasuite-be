package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProductVariant> {

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findBySkuIgnoreCase(String sku);

    List<ProductVariant> findBySkuIn(Collection<String> skus);

    List<ProductVariant> findByProductId(UUID productId);

    List<ProductVariant> findByProductIdIn(Collection<UUID> productIds);

    boolean existsBySku(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuAndVariantIdNot(String sku, UUID variantId);

    @Modifying
    @Query("DELETE FROM ProductVariant pv WHERE pv.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);

    Page<ProductVariant> findByProductIdAndStatusNot(UUID productId, VariantStatus status, Pageable pageable);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status <> 'DELETED' " +
            "AND (:status IS NULL OR v.status = :status) " +
            "AND (:keyword IS NULL " +
            "OR lower(v.sku) LIKE cast(:keyword as String) " +
            "OR lower(v.variantName) LIKE cast(:keyword as String) " +
            "OR lower(coalesce(v.color, '')) LIKE cast(:keyword as String))")
    Page<ProductVariant> search(
            @Param("productId") UUID productId,
            @Param("keyword") String keyword,
            @Param("status") VariantStatus status,
            Pageable pageable);

    long countByProductIdAndStatusNot(UUID productId, VariantStatus status);

    long countByProductIdAndStatus(UUID productId, VariantStatus status);

    long countByProductIdAndStatusNotAndSellingPriceIsNull(UUID productId, VariantStatus status);

    @Query("SELECT DISTINCT pv.color FROM ProductVariant pv WHERE pv.color IS NOT NULL AND pv.color <> '' AND cast(pv.status as string) = 'ACTIVE' ORDER BY pv.color ASC")
    List<String> findDistinctActiveColors();

    @Query("SELECT DISTINCT pv.size FROM ProductVariant pv WHERE pv.size IS NOT NULL AND pv.size <> '' AND cast(pv.status as string) = 'ACTIVE' ORDER BY pv.size ASC")
    List<String> findDistinctActiveSizes();

    @Query("SELECT MIN(pv.sellingPrice) FROM ProductVariant pv WHERE cast(pv.status as string) = 'ACTIVE'")
    BigDecimal findMinActiveSellingPrice();

    @Query("SELECT MAX(pv.sellingPrice) FROM ProductVariant pv WHERE cast(pv.status as string) = 'ACTIVE'")
    BigDecimal findMaxActiveSellingPrice();

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE cast(pv.status as string) = 'ACTIVE' AND pv.sellingPrice >= :minPrice AND pv.sellingPrice <= :maxPrice")
    Set<UUID> findProductIdsByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE cast(pv.status as string) = 'ACTIVE' AND LOWER(pv.color) = LOWER(:color)")
    Set<UUID> findProductIdsByColor(@Param("color") String color);

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE cast(pv.status as string) = 'ACTIVE' AND LOWER(pv.size) = LOWER(:size)")
    Set<UUID> findProductIdsBySize(@Param("size") String size);

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE cast(pv.status as string) = 'ACTIVE' AND (LOWER(pv.sku) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(pv.variantName) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Set<UUID> findProductIdsByVariantKeyword(@Param("kw") String keyword);
}
