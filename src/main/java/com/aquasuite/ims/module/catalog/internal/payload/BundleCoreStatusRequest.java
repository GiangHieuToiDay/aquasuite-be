package com.aquasuite.ims.module.catalog.internal.payload;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import java.util.*;

public record BundleCoreStatusRequest(
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
                        using = BundleCoreCreateRequest.StrictLong.class)
                @NotNull
                @PositiveOrZero
                Long expectedVersion,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictStatus.class)
                @NotNull
                Status productStatus) {
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public static class StrictStatus
            extends com.fasterxml.jackson.databind.JsonDeserializer<Status> {
        @Override
        public Status deserialize(
                com.fasterxml.jackson.core.JsonParser p,
                com.fasterxml.jackson.databind.DeserializationContext c)
                throws java.io.IOException {
            if (!p.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_STRING))
                return c.reportInputMismatch(Status.class, "Expected ACTIVE or INACTIVE");
            try {
                return Status.valueOf(p.getText());
            } catch (IllegalArgumentException ex) {
                return c.reportInputMismatch(Status.class, "Expected ACTIVE or INACTIVE");
            }
        }
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
