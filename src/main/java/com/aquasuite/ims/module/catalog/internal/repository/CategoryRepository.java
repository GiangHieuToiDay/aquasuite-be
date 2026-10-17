package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.status = 'ACTIVE'")
    List<Category> findAllActive();

    Optional<Category> findByCategoryCodeIgnoreCase(String categoryCode);

    List<Category> findByCategoryCodeIn(Collection<String> categoryCodes);
}
