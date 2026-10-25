package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationItemRequest {

    @NotNull(message = "variantId is required")
    private UUID variantId;

    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.01", message = "quantity must be at least 0.01")
    private BigDecimal quantity;

    @Builder.Default
    @DecimalMin(value = "0", message = "discountRate must be >= 0")
    @DecimalMax(value = "100", message = "discountRate must be <= 100")
    private BigDecimal discountRate = BigDecimal.ZERO;
}
