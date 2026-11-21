package com.aquasuite.ims.dto.crm.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CustomerAddressDto(
        UUID addressId,
        UUID customerId,
        String recipientName,
        String phone,
        String province,
        String district,
        String ward,
        String streetAddress,
        String addressText,
        String label,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {}
