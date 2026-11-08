package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, UUID>, JpaSpecificationExecutor<Quotation> {

    Optional<Quotation> findByQuotationCodeAndRevisionNo(String quotationCode, Integer revisionNo);

    List<Quotation> findByQuotationCodeOrderByRevisionNoDesc(String quotationCode);

    Optional<Quotation> findTopByQuotationCodeStartingWithOrderByQuotationCodeDesc(String prefix);

    long countByQuotationCodeStartingWith(String prefix);

    boolean existsByQuotationCodeAndStatusIn(String quotationCode, Collection<QuotationStatus> statuses);

    List<Quotation> findByQuotationCodeAndQuotationIdNot(String quotationCode, UUID quotationId);
}
