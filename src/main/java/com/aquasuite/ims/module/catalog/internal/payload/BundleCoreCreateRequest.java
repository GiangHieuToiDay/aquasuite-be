package com.aquasuite.ims.module.catalog.internal.payload;

import com.aquasuite.ims.module.catalog.internal.payload.BundleCoreCreateRequest.Item;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

public record BundleCoreCreateRequest(
        @NotBlank @Size(max = 100)
        String productCode,
        @NotBlank @Size(max = 255)
        String name,
        @NotNull UUID categoryId,
        UUID brandId,
        @Size(max = 10000)
        String description,
        @NotNull @Size(min = 1, max = 100)
        List<@NotNull @Valid Item> items,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                        using = BundleCoreCreateRequest.StrictMoney.class)
                @NotNull
                @DecimalMin(value = "0", inclusive = false)
                @Digits(integer = 13, fraction = 2)
                BigDecimal packagePrice) {
    public BundleCoreCreateRequest {
        productCode = productCode == null ? null : productCode.trim();
        name = name == null ? null : name.trim();
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }

    public record Item(
            @NotNull UUID variantId,
            @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class)
                    @NotNull
                    @Min(1)
                    @Max(10000)
                    Integer quantity) {
        @JsonAnySetter
        public void unknown(String key, Object value) {
            throw new IllegalArgumentException("Unknown field: " + key);
        }
    }

    public static class StrictInteger
            extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(
                com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext c)
                throws java.io.IOException {
            if (!p.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT))
                return c.reportInputMismatch(Integer.class, "Expected an integer JSON number");
            return p.getIntValue();
        }
    }

    public static class StrictLong extends com.fasterxml.jackson.databind.JsonDeserializer<Long> {
        @Override
        public Long deserialize(
                com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext c)
                throws java.io.IOException {
            if (!p.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT))
                return c.reportInputMismatch(Long.class, "Expected an integer JSON number");
            return p.getLongValue();
        }
    }

    public static class StrictMoney
            extends com.fasterxml.jackson.databind.JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(
                com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext c)
                throws java.io.IOException {
            if (!p.currentToken().isNumeric())
                return c.reportInputMismatch(BigDecimal.class, "Expected a JSON number");
            return p.getDecimalValue();
        }
    }
}
