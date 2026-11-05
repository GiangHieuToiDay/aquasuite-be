package com.aquasuite.ims.module.iam.internal.payload.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Password is used only for this request and must never be logged or persisted. */
@Data
public class CreateCreditApprovalGrantRequest {
    @NotBlank @Email @Size(max = 320)
    private String ownerEmail;

    @NotBlank @Size(max = 100)
    private String password;

    @NotNull
    private UUID customerId;

    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal creditLimit;

    @NotNull @Min(1) @Max(30)
    private Integer creditTermDays;
}
