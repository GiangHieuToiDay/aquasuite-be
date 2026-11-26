package com.aquasuite.ims.dto.inventory.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ManualStockAdjustRequest(
        @NotNull(message = "variantId không được để trống") UUID variantId,
        @NotNull(message = "deltaQty không được để trống")
        @Digits(integer = 10, fraction = 2, message = "Số lượng điều chỉnh tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        BigDecimal deltaQty,
        @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
        String reason) {}
