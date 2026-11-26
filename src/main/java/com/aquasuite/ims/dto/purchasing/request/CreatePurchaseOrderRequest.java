package com.aquasuite.ims.dto.purchasing.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Nhà cung cấp không được để trống")
    private UUID supplierId;

    private UUID warehouseId;

    @Pattern(regexp = "^([A-Za-z0-9_-]{1,50})?$", message = "Mã kho không hợp lệ")
    private String warehouseCode;

    @FutureOrPresent(message = "Ngày dự kiến nhận không được ở trong quá khứ")
    private LocalDate expectedDate;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;

    @NotEmpty(message = "Đơn mua hàng phải có ít nhất một dòng sản phẩm")
    @Size(max = 500, message = "Đơn mua hàng tối đa 500 dòng sản phẩm")
    private List<@NotNull(message = "Dòng sản phẩm không được để trống") @Valid CreatePurchaseOrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePurchaseOrderItemRequest {
        @NotNull(message = "SKU không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số lượng tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal quantity;
    }
}