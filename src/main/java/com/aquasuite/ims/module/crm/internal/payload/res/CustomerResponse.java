package com.aquasuite.ims.module.crm.internal.payload.res;

import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import lombok.Builder;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CustomerResponse(
        UUID customerId,
        UUID userId,
        String customerCode,
        String customerName,
        String phone,
        String email,
        CustomerType customerType,
        String taxCode,
        String companyName,
        String address,
        BigDecimal creditLimit,
        Integer creditTermDays,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

