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

/** Cấu hình chia đợt hóa đơn DRAFT khi chuyển đổi báo giá sang đơn công trình. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitialInvoiceBatchRequest {

    private String batchName;
    private OffsetDateTime scheduledDeliveryDate;
    private String note;

    @Valid
    @NotEmpty(message = "Mỗi đợt hóa đơn phải có ít nhất 1 sản phẩm")
    private List<BatchItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItemRequest {
        @NotNull(message = "variantId không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        private BigDecimal quantity;
    }
}

