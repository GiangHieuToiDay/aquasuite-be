package com.aquasuite.ims.module.crm.internal.payload.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(level = PRIVATE)
public class ApproveCustomerCreditProfileRequest {
    @Size(max = 500)
    String reviewNote;
}
