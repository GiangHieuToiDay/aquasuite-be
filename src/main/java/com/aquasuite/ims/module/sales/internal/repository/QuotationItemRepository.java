package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationItemRepository extends JpaRepository<QuotationItem, UUID> {

    List<QuotationItem> findByQuotationIdOrderBySortOrderAsc(UUID quotationId);

    List<QuotationItem> findByQuotationIdIn(List<UUID> quotationIds);

    // One statement avoids deleting component entities twice after the parent FK cascades.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from QuotationItem item where item.quotationId = :quotationId")
    void deleteByQuotationId(@org.springframework.data.repository.query.Param("quotationId") UUID quotationId);
}
