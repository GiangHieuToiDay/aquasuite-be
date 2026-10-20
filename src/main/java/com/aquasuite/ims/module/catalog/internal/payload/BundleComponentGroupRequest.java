package com.aquasuite.ims.module.catalog.internal.payload;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.*;

/** Same-price option whitelist for one default component. */
public record BundleComponentGroupRequest(
        @NotNull UUID groupId,
        @NotNull UUID defaultVariantId,
        @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> allowedVariantIds) {
    @JsonAnySetter
    public void unknown(String key, Object value) { throw new IllegalArgumentException("Unknown field: " + key); }
}
