package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByCustomerIdInAndStatusIgnoreCase(Collection<UUID> customerIds, String status);

    @Query("select coalesce(sum(transaction.amount), 0) from Transaction transaction "
            + "where transaction.orderId = :orderId and upper(transaction.status) = 'VERIFIED'")
    BigDecimal sumVerifiedAmountByOrderId(@Param("orderId") UUID orderId);
}
