package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationItemRepository extends JpaRepository<QuotationItem, UUID> {

    List<QuotationItem> findByQuotationIdOrderBySortOrderAsc(UUID quotationId);

    void deleteByQuotationId(UUID quotationId);
}
