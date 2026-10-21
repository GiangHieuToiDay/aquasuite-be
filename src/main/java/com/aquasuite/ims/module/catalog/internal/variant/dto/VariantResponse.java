package com.aquasuite.ims.module.catalog.internal.variant.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record VariantResponse(UUID id, UUID productId, String sku, String variantName, String unit,
                              BigDecimal conversionRate, String color, String size, BigDecimal weight,
                              Map<String, Object> technicalSpec, BigDecimal costPrice, BigDecimal sellingPrice,
                              UUID warrantyPolicyId, boolean serialTracking, String status) {
}
