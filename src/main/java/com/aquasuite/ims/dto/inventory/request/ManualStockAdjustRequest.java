package com.aquasuite.ims.dto.inventory.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ManualStockAdjustRequest(
        @NotNull(message = "variantId không được để trống") UUID variantId,
        @NotNull(message = "deltaQty không được để trống") BigDecimal deltaQty,
        String reason) {}
