package com.aquasuite.ims.module.catalog.internal.payload.bundle.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/**
 * Yêu cầu xem trước cho quantity combo; nhóm không có trong selections dùng SKU mặc định.
 * expectedVersion là tùy chọn để phát hiện cấu hình đã đổi kể từ lần client đọc trước.
 */
public record BundleEvaluationRequest(
        @JsonDeserialize(using = BundleJsonDeserializers.StrictInteger.class)
                @NotNull
                @Min(1)
                @Max(10000)
                Integer quantity,
        @JsonDeserialize(using = BundleJsonDeserializers.StrictLong.class) @PositiveOrZero
                Long expectedVersion,
        @Size(max = 100) List<@NotNull @Valid Selection> selections) {
    public BundleEvaluationRequest {
        selections = selections == null ? List.of() : List.copyOf(selections);
    }

    public record Selection(@NotNull UUID groupId, @NotNull UUID variantId) {
        @JsonAnySetter
        public void unknown(String key, Object value) {
            throw new IllegalArgumentException("Unknown field: " + key);
        }
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
