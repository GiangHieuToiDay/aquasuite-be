package com.aquasuite.ims.module.crm.internal.repository;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customer from Customer customer where customer.customerId = :customerId")
    Optional<Customer> findByIdForUpdate(@Param("customerId") UUID customerId);

    Optional<Customer> findByUserId(UUID userId);

    Optional<Customer> findByPhone(String phone);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.phone = :phone")
    Optional<Customer> findByPhoneForUpdate(@Param("phone") String phone);

    /** Unique phone index serializes creation across POS and registration without aborted retries. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO public.customers
            (customer_id, customer_code, customer_name, phone, email, customer_type, status, credit_limit)
        VALUES (:id, :code, :name, :phone, :email, 'RETAIL', 'ACTIVE', 0)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertUnlinkedCustomer(@Param("id") UUID id, @Param("code") String code,
            @Param("name") String name, @Param("phone") String phone, @Param("email") String email);

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
