package com.aquasuite.ims.module.catalog.internal.payload;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.util.*;

/**
 * Whitelist thay thế cho một SKU mặc định; danh sách phải chứa chính SKU mặc định và không trùng.
 * Service kiểm các SKU còn bán được và cùng giá, không cho client chọn SKU ngoài danh sách.
 */
public record BundleComponentGroupRequest(
        @NotNull UUID groupId,
        @NotNull UUID defaultVariantId,
        @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> allowedVariantIds) {
    @JsonAnySetter
    public void unknown(String key, Object value) { throw new IllegalArgumentException("Unknown field: " + key); }
}
