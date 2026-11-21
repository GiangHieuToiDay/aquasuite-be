package com.aquasuite.ims.dto.catalog.request;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record UpdateProductStatusRequest(
        @NotNull(message = "Trạng thái sản phẩm không được để trống")
        ProductStatus status
) {}
