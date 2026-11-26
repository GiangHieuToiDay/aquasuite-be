package com.aquasuite.ims.dto.purchasing.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class UpdatePurchaseOrderRequest {

    private LocalDate expectedDate;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;

    @NotNull(message = "Version không được để trống để kiểm tra xung đột dữ liệu")
    @Min(value = 0, message = "Version phải lớn hơn hoặc bằng 0")
    private Integer version;

    @NotEmpty(message = "Đơn mua hàng phải có ít nhất một dòng sản phẩm")
    @Size(max = 500, message = "Đơn mua hàng tối đa 500 dòng sản phẩm")
    private List<@NotNull(message = "Dòng sản phẩm không được để trống") @Valid UpdatePurchaseOrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePurchaseOrderItemRequest {
        @NotNull(message = "SKU không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số lượng tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal quantity;

        @DecimalMin(value = "0.00", message = "Đơn giá nhập phải lớn hơn hoặc bằng 0")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá nhập tối đa 13 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal unitCost;
    }
}