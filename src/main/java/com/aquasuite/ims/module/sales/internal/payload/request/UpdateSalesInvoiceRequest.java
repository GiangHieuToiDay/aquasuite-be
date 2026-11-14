package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Payload chỉnh sửa hóa đơn khi đang ở trạng thái DRAFT. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSalesInvoiceRequest {

    private String batchName;
    private OffsetDateTime scheduledDeliveryDate;
    private String note;

    @DecimalMin(value = "0.0", message = "Chiết khấu hóa đơn không được âm")
    private BigDecimal discountAmount;

    @Valid
    @NotEmpty(message = "Hóa đơn phải có ít nhất 1 sản phẩm")
    private List<UpdateInvoiceItemInput> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateInvoiceItemInput {
        @NotNull(message = "variantId không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        private BigDecimal quantity;

        @DecimalMin(value = "0.0", message = "Chiết khấu dòng không được âm")
        private BigDecimal discountAmount;
    }
}

