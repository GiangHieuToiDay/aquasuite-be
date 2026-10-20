package com.aquasuite.ims.module.catalog.internal.payload;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
/** Version cấu hình mà client đã đọc; publish/unpublish từ chối version cũ để tránh ghi đè. */
public record BundleVersionRequest(
        @JsonDeserialize(using = BundleJsonDeserializers.StrictLong.class)
        @NotNull @PositiveOrZero Long expectedVersion) {
    @JsonAnySetter public void unknown(String key, Object value) { throw new IllegalArgumentException("Unknown field: " + key); }
}
