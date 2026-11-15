package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn hóa đơn bán hàng theo khách, đơn, đợt giao và khóa cập nhật. */
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
    /**
     * Lấy hóa đơn của các khách được yêu cầu.
     * @param customerIds các mã khách hàng
     * @return danh sách hóa đơn phù hợp
     */
    List<SalesInvoice> findByCustomerIdIn(Collection<UUID> customerIds);

    List<SalesInvoice> findByInvoiceIdIn(Collection<UUID> invoiceIds);
    /**
     * Tìm hóa đơn theo đơn và loại, ví dụ hóa đơn cửa hàng loại STORE.
     * @param orderId mã đơn hàng
     * @param invoiceType loại hóa đơn cần tìm
     * @return hóa đơn phù hợp nếu có
     */
    Optional<SalesInvoice> findByOrderIdAndInvoiceType(UUID orderId, String invoiceType);

    /**
     * Read model for Finance UC-24/UC-63. A Store invoice becomes a financial target only after the external
     * e-invoice provider has returned its identifier and signing time. {@code status} remains the internal
     * delivery-invoice lifecycle, not an e-invoice lifecycle.
     */
    @Query("select invoice from SalesInvoice invoice where invoice.orderId = :orderId "
            + "and upper(invoice.invoiceType) = 'STORE' "
            + "and upper(invoice.status) in ('ISSUED', 'DISPATCHED') "
            + "and invoice.providerInvoiceId is not null and trim(invoice.providerInvoiceId) <> '' "
            + "and invoice.signedAt is not null "
            + "order by invoice.signedAt asc, invoice.invoiceId asc")
    List<SalesInvoice> findElectronicallyIssuedStoreInvoicesByOrderId(@Param("orderId") UUID orderId);

    List<SalesInvoice> findByOrderIdOrderByBatchNoAscCreatedAtAsc(UUID orderId);

    List<SalesInvoice> findByOrderIdAndStatusOrderByBatchNoAscCreatedAtAsc(UUID orderId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM SalesInvoice si WHERE si.invoiceId = :invoiceId")
    Optional<SalesInvoice> findByInvoiceIdForUpdate(@Param("invoiceId") UUID invoiceId);

    boolean existsByOrderIdAndStatus(UUID orderId, String status);

    long countByOrderIdAndStatusNot(UUID orderId, String status);

//    List<SalesInvoice> findByOrderIdOrderByIssueDateDesc(UUID orderId);

    List<SalesInvoice> findByOrderIdOrderByIssueDateDesc(UUID orderId);

//    List<SalesInvoice> findByOrderIdOrderByIssueDateDesc(UUID orderId);
}
