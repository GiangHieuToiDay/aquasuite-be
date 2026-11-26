package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    /** Catalog selection ID: a real variant ID, or bundle Product ID for a combo. */
    @NotNull(message = "variantId is required")
    private UUID variantId;

    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.01", message = "quantity must be at least 0.01")
    @Digits(integer = 10, fraction = 2, message = "quantity max 10 integer digits and 2 decimals")
    private BigDecimal quantity;

    @Builder.Default
    @DecimalMin(value = "0", message = "discountRate must be >= 0")
    @DecimalMax(value = "100", message = "discountRate must be <= 100")
    @Digits(integer = 3, fraction = 2, message = "discountRate max 3 integer digits and 2 decimals")
    private BigDecimal discountRate = BigDecimal.ZERO;
}
