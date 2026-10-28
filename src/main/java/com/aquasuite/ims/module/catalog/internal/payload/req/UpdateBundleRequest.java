package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body thay toàn bộ nội dung combo qua PUT, kèm expectedVersion để chống ghi đè. Luồng: validate
 * như create → service khóa combo/kiểm version → thay definition/items. Mã combo phải giữ nguyên;
 * omit/null brandId hoặc description sẽ xóa giá trị cũ.
 */
public record UpdateBundleRequest(
        @NotBlank @Size(max = 100) String productCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID categoryId,
        UUID brandId,
        @Size(max = 10000) String description,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid BundleItemRequest> items,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2)
                BigDecimal packagePrice,
        @NotNull @PositiveOrZero Long expectedVersion,
        Instant validFrom,
        Instant validUntil) {
    public UpdateBundleRequest {
        productCode = productCode == null ? null : productCode.trim();
        name = name == null ? null : name.trim();
    }

    /** PUT thay toàn bộ cấu hình: overload này xóa lịch. */
    public UpdateBundleRequest(
            String productCode,
            String name,
            UUID categoryId,
            UUID brandId,
            String description,
            List<BundleItemRequest> items,
            BigDecimal packagePrice,
            Long expectedVersion) {
        this(
                productCode,
                name,
                categoryId,
                brandId,
                description,
                items,
                packagePrice,
                expectedVersion,
                null,
                null);
    }

    @JsonAnySetter
    public void unknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown field: " + key);
    }
}
