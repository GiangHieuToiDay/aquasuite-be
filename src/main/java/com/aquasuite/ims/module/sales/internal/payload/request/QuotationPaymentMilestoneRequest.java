package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotationPaymentMilestoneRequest {

    @NotBlank(message = "stageName is required")
    private String stageName;

    @NotNull(message = "percent is required")
    @DecimalMin(value = "0.01", message = "percent must be at least 0.01")
    @DecimalMax(value = "100.00", message = "percent must be at most 100.00")
    private BigDecimal percent;

    private Integer sortOrder;
}

