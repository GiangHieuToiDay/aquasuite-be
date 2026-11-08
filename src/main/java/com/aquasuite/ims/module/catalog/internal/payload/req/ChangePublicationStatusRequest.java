package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

public record ChangePublicationStatusRequest(
        @NotNull Status publicationStatus, @NotNull @PositiveOrZero Long expectedVersion) {
    public enum Status {
        PUBLISHED,
        UNPUBLISHED
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
