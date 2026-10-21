package com.aquasuite.ims.module.catalog.internal.variant.dto;

import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import jakarta.validation.constraints.NotNull;

public record VariantStatusRequest(@NotNull VariantStatus status) {
}
