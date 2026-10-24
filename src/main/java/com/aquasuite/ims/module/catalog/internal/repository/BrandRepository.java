package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;


import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.status = com.aquasuite.ims.module.catalog.internal.entity.enums.BrandStatus.ACTIVE")
    List<Brand> findAllActive();

    Optional<Brand> findByBrandCodeIgnoreCase(String brandCode);

    List<Brand> findByBrandCodeIn(Collection<String> brandCodes);

    boolean existsByBrandCodeIgnoreCase(String brandCode);

    @Query("SELECT COUNT(b) > 0 FROM Brand b " +
           "WHERE lower(trim(b.brandName)) = lower(trim(:name)) " +
           "AND (:excludeId IS NULL OR b.brandId <> :excludeId)")
    boolean existsBrandNameDuplicate(@Param("name") String name, @Param("excludeId") UUID excludeId);

    @Query("SELECT b FROM Brand b " +
           "WHERE (:status IS NULL OR cast(b.status as string) = :status) " +
           "AND (:search IS NULL OR lower(b.brandCode) LIKE lower(concat('%', cast(:search as string), '%')) " +
           "     OR lower(b.brandName) LIKE lower(concat('%', cast(:search as string), '%')))")
    Page<Brand> searchBrands(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );

}
