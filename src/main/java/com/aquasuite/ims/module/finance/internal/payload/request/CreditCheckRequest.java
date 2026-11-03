package com.aquasuite.ims.module.finance.internal.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditCheckRequest(
        UUID orderId,
        @NotBlank String orderChannel,
        @NotNull @DecimalMin(value = "0.01") BigDecimal newOrderAmount) {}
