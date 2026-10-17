package com.aquasuite.ims.module.inventory.internal.payload.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
public record CreateStockCountRequest(
        String warehouseCode,
        String note,

        @NotEmpty(message = "Danh sách sản phẩm kiểm kê không được để trống")
        List<@Valid StockCountItemRequest> items
) {}
