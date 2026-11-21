package com.aquasuite.ims.dto.fulfillment.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DeliveryIssueRequest(
        @NotNull(message = "Mã đợt giao không được để trống") UUID deliveryId,
        @NotBlank(message = "Kho xuất không được để trống") @Pattern(regexp = "WH_MAIN", message = "Kho xuất phải là WH_MAIN") String warehouseCode,
        @NotBlank(message = "Tên người nhận không được để trống") @Size(max = 255, message = "Tên người nhận tối đa 255 ký tự") String recipientName,
        @NotBlank(message = "Số điện thoại người nhận không được để trống") @Size(max = 32, message = "Số điện thoại người nhận tối đa 32 ký tự") String recipientPhone,
        @NotBlank(message = "Địa chỉ giao hàng không được để trống") @Size(max = 2000, message = "Địa chỉ giao hàng tối đa 2000 ký tự") String deliveryAddress,
        OffsetDateTime scheduledDate,
        @NotNull(message = "Tiền COD không được để trống") @DecimalMin(value = "0", message = "Tiền COD không được âm") @Digits(integer = 13, fraction = 0, message = "Tiền COD phải là số nguyên VND, tối đa 13 chữ số") BigDecimal codAmount,
        @Size(max = 1000) String note,
        @NotEmpty(message = "Đợt giao phải có ít nhất một dòng hàng") @Size(max = 100, message = "Đợt giao tối đa 100 dòng hàng") List<@NotNull(message = "Dòng giao hàng không được để trống") @Valid Line> items) {
    public record Line(@NotNull(message = "Dòng đơn hàng không được để trống") UUID orderItemId,
                       @NotNull(message = "Số lượng giao không được để trống") @DecimalMin(value = "0.01", message = "Số lượng giao phải từ 0.01 trở lên") @Digits(integer = 10, fraction = 2, message = "Số lượng giao tối đa 10 chữ số nguyên và 2 chữ số thập phân") BigDecimal quantity) {}
}
