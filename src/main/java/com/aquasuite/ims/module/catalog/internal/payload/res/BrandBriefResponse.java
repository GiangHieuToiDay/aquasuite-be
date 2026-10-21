package com.aquasuite.ims.module.catalog.internal.payload.res;

import lombok.Builder;

import java.util.UUID;

@Builder
public record BrandBriefResponse(
        UUID brandId,
        String brandCode,
        String brandName
) {}
