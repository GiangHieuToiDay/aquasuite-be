package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body tạo combo: mã, tên, danh mục, thành phần và giá gói. Luồng: Jackson đọc JSON theo cấu hình
 * chung → Bean Validation → BundleAdminService.createBundle. Không nhận expectedVersion hoặc trạng
 * thái do client đặt; server tạo ACTIVE/DRAFT.
 */
public record CreateBundleRequest(
        @NotBlank @Size(max = 100) String productCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID categoryId,
        UUID brandId,
        @Size(max = 10000) String description,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid BundleItemRequest> items,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2)
                BigDecimal packagePrice,
        Instant validFrom,
        Instant validUntil) {
    public CreateBundleRequest {
        productCode = productCode == null ? null : productCode.trim();
        name = name == null ? null : name.trim();
    }

    /** Tạo nháp chưa có lịch công bố. */
    public CreateBundleRequest(
            String productCode,
            String name,
            UUID categoryId,
            UUID brandId,
            String description,
            List<BundleItemRequest> items,
            BigDecimal packagePrice) {
        this(productCode, name, categoryId, brandId, description, items, packagePrice, null, null);
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
