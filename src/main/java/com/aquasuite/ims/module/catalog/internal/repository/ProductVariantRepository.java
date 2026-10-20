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

    @Query("SELECT DISTINCT pv.color FROM ProductVariant pv WHERE pv.color IS NOT NULL AND pv.color <> '' AND pv.status = 'ACTIVE' ORDER BY pv.color ASC")
    List<String> findDistinctActiveColors();

    @Query("SELECT DISTINCT pv.size FROM ProductVariant pv WHERE pv.size IS NOT NULL AND pv.size <> '' AND pv.status = 'ACTIVE' ORDER BY pv.size ASC")
    List<String> findDistinctActiveSizes();

    @Query("SELECT MIN(pv.sellingPrice) FROM ProductVariant pv WHERE pv.status = 'ACTIVE'")
    java.math.BigDecimal findMinActiveSellingPrice();

    @Query("SELECT MAX(pv.sellingPrice) FROM ProductVariant pv WHERE pv.status = 'ACTIVE'")
    java.math.BigDecimal findMaxActiveSellingPrice();

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE pv.status = 'ACTIVE' AND pv.sellingPrice >= :minPrice AND pv.sellingPrice <= :maxPrice")
    java.util.Set<UUID> findProductIdsByPriceRange(@Param("minPrice") java.math.BigDecimal minPrice, @Param("maxPrice") java.math.BigDecimal maxPrice);

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE pv.status = 'ACTIVE' AND LOWER(pv.color) = LOWER(:color)")
    java.util.Set<UUID> findProductIdsByColor(@Param("color") String color);

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE pv.status = 'ACTIVE' AND LOWER(pv.size) = LOWER(:size)")
    java.util.Set<UUID> findProductIdsBySize(@Param("size") String size);

    @Query("SELECT DISTINCT pv.productId FROM ProductVariant pv WHERE pv.status = 'ACTIVE' AND (LOWER(pv.sku) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(pv.variantName) LIKE LOWER(CONCAT('%', :kw, '%')))")
    java.util.Set<UUID> findProductIdsByVariantKeyword(@Param("kw") String keyword);
}
