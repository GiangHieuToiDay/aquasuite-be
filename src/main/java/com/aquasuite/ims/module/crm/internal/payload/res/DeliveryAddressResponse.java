package com.aquasuite.ims.module.crm.internal.payload.res;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record DeliveryAddressResponse(
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
