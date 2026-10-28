package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import java.util.*;

/** Read-only preview for fixed component SKUs; no client prices or selections. */
public record BundleEvaluationRequest(
        @NotNull @Min(1) @Max(10000) Integer quantity, @PositiveOrZero Long expectedVersion) {
    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
