package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/** Lưu các dòng hàng được sao lại tại thời điểm tạo hóa đơn cửa hàng. */
public interface SalesInvoiceItemRepository extends JpaRepository<SalesInvoiceItem, UUID> {
}
