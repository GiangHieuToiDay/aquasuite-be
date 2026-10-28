package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Một dòng thành phần dùng chung trong body create/update: variantId và quantity. Jackson chỉ nhận
 * số nguyên JSON, Bean Validation kiểm 1..10000; service kiểm SKU/trùng/nesting. Client chọn SKU và
 * số lượng, không tự gửi giá lẻ, tên hoặc sellable.
 */
public record BundleItemRequest(
        @NotNull UUID variantId, @NotNull @Min(1) @Max(10000) Integer quantity) {
    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
