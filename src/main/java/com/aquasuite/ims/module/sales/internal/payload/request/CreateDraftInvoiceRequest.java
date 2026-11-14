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

/** Payload tạo thêm 1 đợt hóa đơn DRAFT cho đơn hàng đang PROCESSING. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDraftInvoiceRequest {

    private String batchName;
    private OffsetDateTime scheduledDeliveryDate;
    private String note;

    /**
     * Nếu chỉ định splitFromInvoiceId (một hóa đơn DRAFT khác cùng đơn),
     * số lượng của đợt mới sẽ được tách/giảm tương ứng từ hóa đơn DRAFT nguồn đó.
     * Nếu null, các dòng hàng sẽ được thêm mới vào đơn (giữ thêm tồn kho và cộng vào tổng đơn).
     */
    private UUID splitFromInvoiceId;

    @Valid
    @NotEmpty(message = "Hóa đơn phải có ít nhất 1 sản phẩm")
    private List<InvoiceLineInput> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceLineInput {
        @NotNull(message = "variantId không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        private BigDecimal quantity;

        @DecimalMin(value = "0.0", message = "Chiết khấu dòng không được âm")
        private BigDecimal discountAmount;
    }
}

