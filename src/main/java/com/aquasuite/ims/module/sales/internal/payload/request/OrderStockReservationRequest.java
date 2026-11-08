package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public record OrderStockReservationRequest(
        @NotNull UUID requestId,
        @NotNull @Pattern(regexp="[A-Z0-9_-]{1,50}") String warehouseCode,
        @NotEmpty @Size(max=200) List<@NotNull @Valid Line> lines) {
    public record Line(@NotNull UUID orderItemId,
                       @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal quantity) {}
}
