package com.aquasuite.ims.dto.crm.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CustomerCreditProfileResponse(
        UUID customerId,
        BigDecimal creditLimit,
        Integer creditTermDays,
        UUID requestedBy,
        UUID approvedBy) {}
