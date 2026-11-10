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
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Nhà cung cấp không được để trống")
    private UUID supplierId;

    private UUID warehouseId;

    private String warehouseCode;

    private LocalDate expectedDate;

    private String note;

    private String deliveryPlanType;

    @Valid
    private List<CreatePurchaseOrderStageRequest> stages;

    @NotEmpty(message = "Đơn mua hàng phải có ít nhất một dòng sản phẩm")
    @Valid
    private List<CreatePurchaseOrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePurchaseOrderItemRequest {
        @NotNull(message = "SKU không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        private BigDecimal quantity;
    }
}