package com.aquasuite.ims.module.catalog.internal.payload.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record CreateProductVariantRequest(
        @NotBlank(message = "Mã SKU không được để trống")
        @Size(max = 100, message = "Mã SKU tối đa 100 ký tự")
        String sku,

        @NotBlank(message = "Tên biến thể không được để trống")
        @Size(max = 255, message = "Tên biến thể tối đa 255 ký tự")
        String variantName,

        @NotBlank(message = "Đơn vị tính không được để trống")
        @Size(max = 50, message = "Đơn vị tính tối đa 50 ký tự")
        String unit,

        BigDecimal conversionRate,

        String color,

        String size,

        BigDecimal weight,

        Map<String, Object> technicalSpec,

        @DecimalMin(value = "0.0", message = "Giá vốn không được âm")
        BigDecimal costPrice,

        @NotNull(message = "Giá bán không được để trống")
        @DecimalMin(value = "0.0", inclusive = false, message = "Giá bán phải lớn hơn 0")
        BigDecimal sellingPrice,

        UUID warrantyPolicyId,

        Boolean serialTracking,

        List<String> imageUrls,

        BigDecimal initialStock,

        BigDecimal minimumQty
) {}
