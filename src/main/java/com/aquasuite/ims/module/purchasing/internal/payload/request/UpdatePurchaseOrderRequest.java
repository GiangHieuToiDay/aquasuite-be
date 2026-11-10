package com.aquasuite.ims.module.purchasing.internal.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    private String note;

    private String deliveryPlanType;

    @Valid
    private List<CreatePurchaseOrderStageRequest> stages;

    @NotNull(message = "Version không được để trống để kiểm tra xung đột dữ liệu")
    private Integer version;

    @NotEmpty(message = "Đơn mua hàng phải có ít nhất một dòng sản phẩm")
    @Valid
    private List<UpdatePurchaseOrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePurchaseOrderItemRequest {
        @NotNull(message = "SKU không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        private BigDecimal quantity;

        @DecimalMin(value = "0.00", message = "Đơn giá nhập phải lớn hơn hoặc bằng 0")
        private BigDecimal unitCost;
    }
}