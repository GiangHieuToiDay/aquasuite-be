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

/** Payload tạo thêm 1 đợt hóa đơn DRAFT cho đơn hàng đang PROCESSING. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDraftInvoiceRequest {

    @Size(max = 255, message = "Tên đợt giao tối đa 255 ký tự")
    private String batchName;
    @FutureOrPresent(message = "Ngày giao dự kiến không được ở trong quá khứ")
    private OffsetDateTime scheduledDeliveryDate;
    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;

    /**
     * Nếu chỉ định splitFromInvoiceId (một hóa đơn DRAFT khác cùng đơn),
     * số lượng của đợt mới sẽ được tách/giảm tương ứng từ hóa đơn DRAFT nguồn đó.
     * Nếu null, các dòng hàng sẽ được thêm mới vào đơn (giữ thêm tồn kho và cộng vào tổng đơn).
     */
    private UUID splitFromInvoiceId;

    @NotEmpty(message = "Hóa đơn phải có ít nhất 1 sản phẩm")
    @Size(max = 200, message = "Hóa đơn tối đa 200 dòng sản phẩm")
    private List<@NotNull(message = "Dòng sản phẩm không được để trống") @Valid InvoiceLineInput> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceLineInput {
        @NotNull(message = "variantId không được để trống")
        private UUID variantId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0.01", message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 10, fraction = 2, message = "Số lượng tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal quantity;

        @DecimalMin(value = "0.0", message = "Chiết khấu dòng không được âm")
        @Digits(integer = 13, fraction = 2, message = "Chiết khấu dòng tối đa 13 chữ số nguyên và 2 chữ số thập phân")
        private BigDecimal discountAmount;
    }
}

