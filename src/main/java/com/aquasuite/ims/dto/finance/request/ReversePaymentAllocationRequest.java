package com.aquasuite.ims.dto.finance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mandatory evidence for an Accountant correction of an allocation. */
public record ReversePaymentAllocationRequest(
        @NotBlank @Size(max = 1000) String reason
) {}
