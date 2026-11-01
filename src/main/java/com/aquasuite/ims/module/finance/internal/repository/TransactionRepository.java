package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionCode(String transactionCode);

    List<Transaction> findByOrderId(UUID orderId);

    boolean existsByTransactionCode(String transactionCode);

    long countByTransactionCodeStartingWith(String prefix);
}
