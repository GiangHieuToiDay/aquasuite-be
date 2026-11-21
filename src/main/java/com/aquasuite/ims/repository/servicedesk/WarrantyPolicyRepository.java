package com.aquasuite.ims.repository.servicedesk;

import com.aquasuite.ims.entity.servicedesk.WarrantyPolicy;
import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("servicedeskWarrantyPolicyRepository")
public interface WarrantyPolicyRepository extends JpaRepository<WarrantyPolicy, UUID> {

    @Query(value = "SELECT wp.* FROM warranty_policies wp " +
            "JOIN brands b ON b.brand_id = wp.brand_id " +
            "JOIN categories c ON c.category_id = wp.category_id " +
            "WHERE (:status IS NULL OR wp.status = CAST(:status AS varchar)) " +
            "AND (:keyword IS NULL OR lower(wp.policy_name) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(b.brand_name) LIKE lower(concat('%', :keyword, '%')) " +
            "OR lower(c.category_name) LIKE lower(concat('%', :keyword, '%'))) ",
            countQuery = "SELECT count(*) FROM warranty_policies wp " +
                    "JOIN brands b ON b.brand_id = wp.brand_id " +
                    "JOIN categories c ON c.category_id = wp.category_id " +
                    "WHERE (:status IS NULL OR wp.status = CAST(:status AS varchar)) " +
                    "AND (:keyword IS NULL OR lower(wp.policy_name) LIKE lower(concat('%', :keyword, '%')) " +
                    "OR lower(b.brand_name) LIKE lower(concat('%', :keyword, '%')) " +
                    "OR lower(c.category_name) LIKE lower(concat('%', :keyword, '%'))) ", nativeQuery = true)
    Page<WarrantyPolicy> search(@Param("keyword") String keyword,
                                @Param("status") String status,
                                Pageable pageable);

    boolean existsByBrandIdAndCategoryIdAndStatusAndWarrantyPolicyIdNot(UUID brandId, UUID categoryId,
                                                                         WarrantyPolicyStatus status, UUID warrantyPolicyId);

    boolean existsByBrandIdAndCategoryIdAndStatus(UUID brandId, UUID categoryId, WarrantyPolicyStatus status);

    Optional<WarrantyPolicy> findByPolicyCode(String policyCode);

    @Query(value = "SELECT nextval('public.warranty_policy_code_seq')", nativeQuery = true)
    long nextPolicyCodeSequence();
}
