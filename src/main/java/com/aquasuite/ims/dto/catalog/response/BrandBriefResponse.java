package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record BrandBriefResponse(
        UUID brandId,
        String brandCode,
        String brandName
) {}
