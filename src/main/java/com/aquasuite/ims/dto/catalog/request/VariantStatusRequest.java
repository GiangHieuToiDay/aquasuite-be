package com.aquasuite.ims.dto.catalog.request;

import com.aquasuite.ims.enums.catalog.VariantStatus;
import jakarta.validation.constraints.NotNull;

public record VariantStatusRequest(@NotNull VariantStatus status) {
}
