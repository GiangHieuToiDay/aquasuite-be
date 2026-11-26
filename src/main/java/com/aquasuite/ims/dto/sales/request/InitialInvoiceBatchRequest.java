package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 255, message = "Tên đợt giao tối đa 255 ký tự")
    private String batchName;
    @FutureOrPresent(message = "Ngày giao dự kiến không được ở trong quá khứ")
    private OffsetDateTime scheduledDeliveryDate;
    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    @NotEmpty(message = "Mỗi đợt hóa đơn phải có ít nhất 1 sản phẩm")
    @Size(max = 200, message = "Mỗi đợt hóa đơn tối đa 200 dòng sản phẩm")
    private List<@NotNull(message = "Dòng sản phẩm không được để trống") @Valid BatchItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItemRequest {
        @NotNull(message = "variantId không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số lượng tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal quantity;
    }
}

