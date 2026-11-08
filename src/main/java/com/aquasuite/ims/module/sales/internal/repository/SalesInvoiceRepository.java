package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

/** Truy vấn hóa đơn bán hàng theo khách, đơn và loại hóa đơn. */
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
    /**
     * Lấy hóa đơn của các khách được yêu cầu.
     * @param customerIds các mã khách hàng
     * @return danh sách hóa đơn phù hợp
     */
    List<SalesInvoice> findByCustomerIdIn(Collection<UUID> customerIds);
    /**
     * Tìm hóa đơn theo đơn và loại, ví dụ hóa đơn cửa hàng loại STORE.
     * @param orderId mã đơn hàng
     * @param invoiceType loại hóa đơn cần tìm
     * @return hóa đơn phù hợp nếu có
     */
    Optional<SalesInvoice> findByOrderIdAndInvoiceType(UUID orderId, String invoiceType);
}
