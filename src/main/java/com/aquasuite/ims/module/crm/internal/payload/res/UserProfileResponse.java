package com.aquasuite.ims.module.crm.internal.payload.res;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        List<String> roles,
        String customerCode,
        String customerTier,
        List<DeliveryAddressResponse> deliveryAddresses
) {}
