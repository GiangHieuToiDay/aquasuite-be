package com.aquasuite.ims.module.crm.internal.repository;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByCustomerCode(String customerCode);

    boolean existsByCustomerCode(String customerCode);

    boolean existsByPhone(String phone);

    List<Customer> findByStatusIgnoreCaseOrderByCustomerNameAsc(String status);

    @Query("""
        SELECT c FROM Customer c
        WHERE (:keyword IS NULL OR LOWER(c.customerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:status IS NULL OR LOWER(c.status) = LOWER(:status))
    """)
    Page<Customer> searchCustomers(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );
}
