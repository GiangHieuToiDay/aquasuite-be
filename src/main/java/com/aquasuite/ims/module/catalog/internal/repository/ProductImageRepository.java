package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByVariantId(UUID variantId);

    List<ProductImage> findByProductId(UUID productId);

    @Modifying
    @Query("DELETE FROM ProductImage pi WHERE pi.variantId = :variantId")
    void deleteByVariantId(@Param("variantId") UUID variantId);
}
