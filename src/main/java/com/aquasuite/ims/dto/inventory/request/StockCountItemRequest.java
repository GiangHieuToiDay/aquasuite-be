package com.aquasuite.ims.dto.inventory.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StockCountItemRequest(
        @NotNull(message = "variantId không được để trống") UUID variantId,
        @NotNull(message = "actualQty không được để trống")
                @jakarta.validation.constraints.DecimalMin(
                        value = "0.0",
                        message = "Số lượng đếm thực tế không được âm")
                @Digits(integer = 10, fraction = 2, message = "Số lượng đếm thực tế tối đa 10 chữ số nguyên và 2 chữ số thập phân")
                BigDecimal actualQty,
        @Size(max = 255, message = "Lý do không được vượt quá 255 ký tự")
        String reason) {}
