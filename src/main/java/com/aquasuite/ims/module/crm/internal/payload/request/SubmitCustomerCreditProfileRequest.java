package com.aquasuite.ims.module.crm.internal.payload.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(level = PRIVATE)
public class SubmitCustomerCreditProfileRequest {
    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "500000000.00")
    BigDecimal creditLimit;

    @NotNull
    @Min(1)
    @Max(30)
    Integer creditTermDays;

    @NotBlank
    @Size(max = 500)
    String reason;

    @Size(max = 32)
    String identityDocumentNumber;

    @Size(max = 500)
    String residentialAddress;

    @Size(max = 500)
    String worksiteAddress;
}
