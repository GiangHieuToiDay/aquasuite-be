package com.aquasuite.ims.module.catalog.internal.payload;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
/** Preview only. Omitted selections use each group's default SKU. */
public record BundleEvaluationRequest(
        @JsonDeserialize(using = BundleJsonDeserializers.StrictInteger.class)
        @NotNull @Min(1) @Max(10000) Integer quantity,
        @JsonDeserialize(using = BundleJsonDeserializers.StrictLong.class)
        @PositiveOrZero Long expectedVersion,
        @Size(max = 100) List<@NotNull @Valid Selection> selections) {
    public BundleEvaluationRequest { selections = selections == null ? List.of() : List.copyOf(selections); }
    public record Selection(@NotNull UUID groupId, @NotNull UUID variantId) {
        @JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("Unknown field: " + key); }
    }
    @JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("Unknown field: " + key); }
}
