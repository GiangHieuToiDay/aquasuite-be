package com.aquasuite.ims.dto.inventory.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
public record CreateStockCountRequest(
        @Pattern(regexp = "^([A-Za-z0-9_-]{1,50})?$", message = "Mã kho không hợp lệ")
        String warehouseCode,
        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
        String note,
        @NotEmpty(message = "Danh sách sản phẩm kiểm kê không được để trống")
        @Size(max = 500, message = "Tối đa 500 sản phẩm trong một phiếu kiểm kê")
                List<@NotNull(message = "Dòng kiểm kê không được để trống") @Valid StockCountItemRequest> items) {}
