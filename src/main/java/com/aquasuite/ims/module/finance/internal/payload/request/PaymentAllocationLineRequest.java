package com.aquasuite.ims.module.finance.internal.payload.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentAllocationLineRequest {
    @NotNull(message = "Invoice ID is required")
    UUID invoiceId;

    @NotNull(message = "Allocated amount is required")
    @DecimalMin(value = "0.01", message = "Allocated amount must be greater than zero")
    BigDecimal allocatedAmount;
}
