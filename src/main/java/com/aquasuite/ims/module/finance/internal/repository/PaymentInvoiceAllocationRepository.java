package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.PaymentInvoiceAllocation;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Finance repository for durable receipt-to-electronic-invoice allocation rows. */
public interface PaymentInvoiceAllocationRepository extends JpaRepository<PaymentInvoiceAllocation, UUID> {
    List<PaymentInvoiceAllocation> findByTransactionIdOrderByAllocatedAtDesc(UUID transactionId);

    List<PaymentInvoiceAllocation> findByTransactionIdAndAllocationStatus(UUID transactionId, PaymentAllocationStatus allocationStatus);

    List<PaymentInvoiceAllocation> findByTransactionIdInAndAllocationStatus(
            Collection<UUID> transactionIds, PaymentAllocationStatus allocationStatus);

    List<PaymentInvoiceAllocation> findByInvoiceIdInAndAllocationStatus(
            Collection<UUID> invoiceIds, PaymentAllocationStatus allocationStatus);

    List<PaymentInvoiceAllocation> findByInvoiceIdAndAllocationStatus(
            UUID invoiceId, PaymentAllocationStatus allocationStatus);

    List<PaymentInvoiceAllocation> findByOrderIdAndAllocationStatus(
            UUID orderId, PaymentAllocationStatus allocationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentInvoiceAllocation a where a.allocationId = :allocationId")
    java.util.Optional<PaymentInvoiceAllocation> findByIdForUpdate(@Param("allocationId") UUID allocationId);

    List<PaymentInvoiceAllocation> findByTransactionIdAndAllocationRequestId(
            UUID transactionId, UUID allocationRequestId);
}
