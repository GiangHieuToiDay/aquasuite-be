package com.aquasuite.ims.module.catalog.internal.payload.res;

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
