package com.aquasuite.ims.module.catalog.internal.variant.dto;

import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record VariantUpsertRequest(@NotBlank String sku, @NotBlank String variantName, @NotBlank String unit,
                                   @NotNull @DecimalMin(value = "0.0001") BigDecimal conversionRate,
                                   String color, String size, @NotNull @DecimalMin("0") BigDecimal weight,
                                   Map<String, Object> technicalSpec, @DecimalMin("0") BigDecimal costPrice,
                                   @DecimalMin("0") BigDecimal sellingPrice, UUID warrantyPolicyId,
                                   Boolean serialTracking, VariantStatus status, boolean acknowledgeBelowCost) { }
