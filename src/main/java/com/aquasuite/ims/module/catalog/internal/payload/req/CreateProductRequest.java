package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record CreateProductRequest(
        @NotBlank(message = "Mã sản phẩm (Product Code) không được để trống")
        @Size(max = 100, message = "Mã sản phẩm tối đa 100 ký tự")
        String productCode,

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
        String productName,

        @NotNull(message = "Danh mục sản phẩm không được để trống")
        UUID categoryId,

        UUID brandId,

        @Size(max = 100, message = "Xuất xứ (origin) tối đa 100 ký tự")
        String origin,

        String description,

        Map<String, Object> specifications,

        ProductType productType,

        ProductStatus status,

        List<String> imageUrls
) {}
