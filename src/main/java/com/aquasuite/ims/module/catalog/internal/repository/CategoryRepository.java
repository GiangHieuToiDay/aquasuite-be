package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import com.aquasuite.ims.module.catalog.internal.repository.projection.CategoryHierarchyProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {


    @Query("SELECT c FROM Category c WHERE c.status = com.aquasuite.ims.module.catalog.internal.enums.CategoryStatus.ACTIVE")
    List<Category> findAllActive();

    Optional<Category> findByCategoryCodeIgnoreCase(String categoryCode);

    List<Category> findByCategoryCodeIn(Collection<String> categoryCodes);

    @Query(value = "SELECT pg_advisory_xact_lock(1128354892, 1)", nativeQuery = true)
    void acquireCategoryAdvisoryLock();

    boolean existsByCategoryCodeIgnoreCase(String categoryCode);

    @Query("SELECT COUNT(c) > 0 FROM Category c " +
           "WHERE c.parent IS NULL " +
           "AND lower(trim(c.categoryName)) = lower(trim(:name)) " +
           "AND (:excludeId IS NULL OR c.categoryId <> :excludeId)")
    boolean existsRootNameDuplicate(@Param("name") String name, @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(c) > 0 FROM Category c " +
           "WHERE c.parent.categoryId = :parentId " +
           "AND lower(trim(c.categoryName)) = lower(trim(:name)) " +
           "AND (:excludeId IS NULL OR c.categoryId <> :excludeId)")
    boolean existsChildNameDuplicate(@Param("parentId") UUID parentId, @Param("name") String name, @Param("excludeId") UUID excludeId);

    boolean existsByParent_CategoryId(UUID parentId);

    @Query("SELECT c.categoryId AS categoryId, c.parent.categoryId AS parentId " +
           "FROM Category c WHERE c.categoryId = :categoryId")
    Optional<CategoryHierarchyProjection> findHierarchyProjectionById(@Param("categoryId") UUID categoryId);

    @Query("SELECT c FROM Category c " +
           "LEFT JOIN FETCH c.parent " +
           "WHERE (:status IS NULL OR cast(c.status as string) = :status) " +
           "AND (:search IS NULL OR lower(c.categoryCode) LIKE lower(concat('%', cast(:search as string), '%')) " +
           "     OR lower(c.categoryName) LIKE lower(concat('%', cast(:search as string), '%')))")
    Page<Category> searchCategories(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable
    );

}
