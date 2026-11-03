package com.aquasuite.ims.module.crm.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Entity-free customer projection used by Finance receivable workflows. */
public record CustomerDebtProfileDto(
        UUID customerId,
        String customerCode,
        String customerName,
        String phone,
        String email,
        String customerType,
        String taxCode,
        String companyName,
        List<String> addresses,
        List<String> projects,
        BigDecimal creditLimit,
        Integer creditTermDays,
        String status) {}
