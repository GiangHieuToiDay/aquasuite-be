package com.aquasuite.ims.dto.catalog.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductImageResponse(
        UUID imageId,
        UUID variantId,
        String imageUrl,
        Boolean isPrimary,
        Integer sortOrder
) {}
