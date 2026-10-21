package com.aquasuite.ims.module.crm.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CustomerSummaryDto(
        UUID customerId,
        UUID userId,
        String customerCode,
        String customerName,
        String phone,
        String email,
        String customerType,
        String companyName,
        String status
) {}
