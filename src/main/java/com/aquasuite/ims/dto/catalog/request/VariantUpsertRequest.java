package com.aquasuite.ims.dto.catalog.request;

import com.aquasuite.ims.enums.catalog.VariantStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record VariantUpsertRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 255) String variantName,
        @NotBlank @Size(max = 50) String unit,
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 8, fraction = 4) BigDecimal conversionRate,
        @Size(max = 100) String color,
        @Size(max = 100) String size,
        @NotNull @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal weight,
        @Size(max = 50) Map<String, Object> technicalSpec,
        @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal costPrice,
        @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal sellingPrice,
        UUID warrantyPolicyId,
        Boolean serialTracking,
        VariantStatus status,
        boolean acknowledgeBelowCost
) {
}