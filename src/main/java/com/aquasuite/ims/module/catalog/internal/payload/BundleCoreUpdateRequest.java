package com.aquasuite.ims.module.catalog.internal.payload;

import com.aquasuite.ims.module.catalog.internal.payload.BundleCoreCreateRequest.Item;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

public record BundleCoreUpdateRequest(
        @NotBlank @Size(max = 100) String productCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID categoryId,
        UUID brandId,
        @Size(max = 10000) String description,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Item> items,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                        using = BundleCoreCreateRequest.StrictMoney.class)
                @NotNull
                @DecimalMin(value = "0", inclusive = false)
                @Digits(integer = 13, fraction = 2)
                BigDecimal packagePrice,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                        using = BundleCoreCreateRequest.StrictLong.class)
                @NotNull
                @PositiveOrZero
                Long expectedVersion) {
    public BundleCoreUpdateRequest {
        productCode = productCode == null ? null : productCode.trim();
        name = name == null ? null : name.trim();
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
