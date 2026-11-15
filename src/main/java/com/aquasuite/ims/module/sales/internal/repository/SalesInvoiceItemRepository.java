package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Lưu các dòng hàng được sao lại tại thời điểm tạo/chỉnh sửa hóa đơn bán hàng. */
public interface SalesInvoiceItemRepository extends JpaRepository<SalesInvoiceItem, UUID> {

    List<SalesInvoiceItem> findByInvoiceIdOrderByInvoiceItemIdAsc(UUID invoiceId);

    List<SalesInvoiceItem> findByInvoiceIdIn(Collection<UUID> invoiceIds);

    void deleteByInvoiceId(UUID invoiceId);

    boolean existsByOrderItemIdAndInvoiceIdIn(UUID orderItemId, Collection<UUID> invoiceIds);
}
