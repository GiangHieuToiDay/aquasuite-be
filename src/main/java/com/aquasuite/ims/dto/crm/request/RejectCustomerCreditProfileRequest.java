package com.aquasuite.ims.dto.crm.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(level = PRIVATE)
public class RejectCustomerCreditProfileRequest {
    @NotBlank
    @Size(max = 500)
    String reviewNote;
}
