package com.aquasuite.ims.dto.catalog.request;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record UpdateProductRequest(
        @NotBlank(message = "Mã sản phẩm không được để trống")
        @Size(max = 100, message = "Mã sản phẩm tối đa 100 ký tự")
        String productCode,

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự")
        String productName,

        @NotNull(message = "Danh mục sản phẩm không được để trống")
        UUID categoryId,

        UUID brandId,

        @Size(max = 5000, message = "Mô tả sản phẩm tối đa 5000 ký tự")
        String description,

        @Size(max = 50, message = "Thông số kỹ thuật tối đa 50 mục")
        Map<String, Object> specifications,

        ProductType productType,

        ProductStatus status,

        @Size(max = 20, message = "Tối đa 20 ảnh sản phẩm")
        List<@NotBlank(message = "URL ảnh không được để trống") @Size(max = 2048, message = "URL ảnh tối đa 2048 ký tự") @Pattern(regexp = "^https?://[^\\s]+$", message = "URL ảnh phải bắt đầu bằng http:// hoặc https://") String> imageUrls
) {}
