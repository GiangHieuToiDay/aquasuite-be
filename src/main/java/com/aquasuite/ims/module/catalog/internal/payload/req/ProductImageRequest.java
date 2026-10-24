package com.aquasuite.ims.module.catalog.internal.payload.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductImageRequest(
        UUID imageId,
        UUID variantId,
        @NotBlank(message = "URL hình ảnh không được để trống")
        String imageUrl,
        Boolean isPrimary,
        Integer sortOrder
) {}
