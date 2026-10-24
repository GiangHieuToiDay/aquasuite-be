package com.aquasuite.ims.module.inventory.internal.payload.req;

import jakarta.validation.constraints.NotNull;
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
                BigDecimal actualQty,
        String reason) {}
