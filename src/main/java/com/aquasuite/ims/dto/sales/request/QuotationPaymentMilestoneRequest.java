package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "stageName max length is 100")
    private String stageName;

    @NotNull(message = "percent is required")
    @DecimalMin(value = "0.01", message = "percent must be at least 0.01")
    @DecimalMax(value = "100.00", message = "percent must be at most 100.00")
    @Digits(integer = 3, fraction = 2, message = "percent max 3 integer digits and 2 decimals")
    private BigDecimal percent;

    @Min(value = 0, message = "sortOrder must be >= 0")
    @Max(value = 1000, message = "sortOrder must be <= 1000")
    private Integer sortOrder;
}

