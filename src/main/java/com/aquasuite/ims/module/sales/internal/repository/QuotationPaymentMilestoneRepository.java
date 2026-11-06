package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.QuotationPaymentMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuotationPaymentMilestoneRepository extends JpaRepository<QuotationPaymentMilestone, UUID> {

    List<QuotationPaymentMilestone> findByQuotationIdOrderBySortOrderAsc(UUID quotationId);

    void deleteByQuotationId(UUID quotationId);
}

