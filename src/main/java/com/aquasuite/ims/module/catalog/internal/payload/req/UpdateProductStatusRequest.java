package com.aquasuite.ims.module.catalog.internal.payload.req;

import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record UpdateProductStatusRequest(
        @NotNull(message = "Trạng thái sản phẩm không được để trống")
        ProductStatus status
) {}
