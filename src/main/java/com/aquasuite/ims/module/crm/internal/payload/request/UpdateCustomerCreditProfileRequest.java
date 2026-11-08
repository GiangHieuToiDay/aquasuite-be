package com.aquasuite.ims.module.crm.internal.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCustomerCreditProfileRequest {
    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal creditLimit;

    @NotNull @Min(1) @Max(30)
    private Integer creditTermDays;

    @NotBlank @Size(max = 512)
    private String approvalToken;

    @NotBlank @Size(max = 500)
    private String reason;
}
