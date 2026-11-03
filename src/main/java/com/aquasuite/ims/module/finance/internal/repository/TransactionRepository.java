package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByCustomerIdInAndStatusIgnoreCase(Collection<UUID> customerIds, String status);
}
