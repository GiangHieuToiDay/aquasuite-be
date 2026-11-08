package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

/**
 * Body bật/tắt combo nội bộ: ACTIVE/INACTIVE và expectedVersion. Luồng: Jackson kiểm kiểu/status →
 * service khóa và kiểm version → đổi Product.status. Không dùng DTO này để xóa hoặc publish; các
 * thao tác đó có contract riêng.
 */
public record ChangeBundleStatusRequest(
        @NotNull @PositiveOrZero Long expectedVersion, @NotNull Status productStatus) {
    /** Các trạng thái client được đổi qua endpoint status; không gồm DELETED hoặc publication. */
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
