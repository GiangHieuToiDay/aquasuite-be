package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gọi khóa PostgreSQL qua Spring Data JPA, giữ khóa đến khi transaction kết thúc. */
public interface FinanceLockRepository extends Repository<Transaction, UUID> {
    /**
     * Khóa mã chống ghi trùng trước khi khóa đơn.
     * @param key khóa thanh toán đã có tiền tố phân biệt
     * @return giá trị cố định sau khi đã lấy khóa
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))) AS locked", nativeQuery = true)
    int lockPaymentKey(@Param("key") String key);

    /**
     * Khóa nghiệp vụ công nợ, sử dụng cùng hàm băm với luồng hiện tại.
     * @param key khóa nghiệp vụ, bao gồm tiền tố xác định loại khóa
     * @return giá trị cố định sau khi đã lấy khóa
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS locked", nativeQuery = true)
    int lockCreditKey(@Param("key") String key);
}
