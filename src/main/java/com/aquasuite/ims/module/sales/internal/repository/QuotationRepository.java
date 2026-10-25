package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    Optional<Quotation> findByQuotationCodeAndRevisionNo(String quotationCode, Integer revisionNo);

    List<Quotation> findByQuotationCodeOrderByRevisionNoDesc(String quotationCode);
}
