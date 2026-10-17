package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.status = 'ACTIVE'")
    List<Brand> findAllActive();

    Optional<Brand> findByBrandCodeIgnoreCase(String brandCode);

    List<Brand> findByBrandCodeIn(Collection<String> brandCodes);
}
