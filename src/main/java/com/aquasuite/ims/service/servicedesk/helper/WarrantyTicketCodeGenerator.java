package com.aquasuite.ims.service.servicedesk.helper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Bộ sinh mã phiếu yêu cầu bảo hành duy nhất dạng {@code TCK-WAR-YYYYMM-XXXX} (UC-47)
 * dựa trên bảng đếm nguyên tử {@code warranty_ticket_code_counters} trên PostgreSQL.
 */
@Component
public class WarrantyTicketCodeGenerator {

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCode(LocalDate date) {
        String ym = date.format(YEAR_MONTH_FMT);
        Number seq = (Number) entityManager.createNativeQuery("""
                INSERT INTO public.warranty_ticket_code_counters (year_month, last_seq)
                VALUES (:ym, 1)
                ON CONFLICT (year_month) DO UPDATE
                SET last_seq = public.warranty_ticket_code_counters.last_seq + 1
                RETURNING last_seq
                """)
                .setParameter("ym", ym)
                .getSingleResult();
        return String.format("TCK-WAR-%s-%04d", ym, seq.intValue());
    }
}

