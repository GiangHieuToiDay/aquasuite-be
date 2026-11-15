package com.aquasuite.ims.module.servicedesk.internal.service.helper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Bộ sinh mã thẻ bảo hành duy nhất dạng {@code WAR-YYYYMM-XXXX} dựa trên bảng đếm nguyên tử trên PostgreSQL.
 */
@Component
public class WarrantyCodeGenerator {

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCode(LocalDate activationDate) {
        String ym = activationDate.format(YEAR_MONTH_FMT);
        Number seq = (Number) entityManager.createNativeQuery("""
                INSERT INTO public.warranty_code_counters (year_month, last_seq)
                VALUES (:ym, 1)
                ON CONFLICT (year_month) DO UPDATE
                SET last_seq = public.warranty_code_counters.last_seq + 1
                RETURNING last_seq
                """)
                .setParameter("ym", ym)
                .getSingleResult();
        return String.format("WAR-%s-%04d", ym, seq.intValue());
    }
}

