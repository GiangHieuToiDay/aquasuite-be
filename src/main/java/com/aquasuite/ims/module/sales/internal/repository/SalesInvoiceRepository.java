package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn hóa đơn bán hàng theo khách, đơn, đợt giao và khóa cập nhật. */
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {

    List<SalesInvoice> findByCustomerIdIn(Collection<UUID> customerIds);

    Optional<SalesInvoice> findByOrderIdAndInvoiceType(UUID orderId, String invoiceType);

    List<SalesInvoice> findByOrderIdOrderByBatchNoAscCreatedAtAsc(UUID orderId);

    List<SalesInvoice> findByOrderIdAndStatusOrderByBatchNoAscCreatedAtAsc(UUID orderId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SalesInvoice si WHERE si.invoiceId = :invoiceId")
    Optional<SalesInvoice> findByInvoiceIdForUpdate(@Param("invoiceId") UUID invoiceId);

    boolean existsByOrderIdAndStatus(UUID orderId, String status);

    long countByOrderIdAndStatusNot(UUID orderId, String status);
}
