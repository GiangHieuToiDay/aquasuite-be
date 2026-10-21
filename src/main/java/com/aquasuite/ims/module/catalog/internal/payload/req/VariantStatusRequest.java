package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import jakarta.validation.constraints.NotNull;

public record VariantStatusRequest(@NotNull VariantStatus status) {
}
