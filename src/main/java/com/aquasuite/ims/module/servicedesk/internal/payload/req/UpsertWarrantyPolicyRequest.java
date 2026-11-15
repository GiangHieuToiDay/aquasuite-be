package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyPolicyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record UpsertWarrantyPolicyRequest(
        @NotBlank @Size(max = 255) String policyName,
        @NotNull UUID brandId,
        @NotNull UUID categoryId,
        @NotNull @Positive Integer warrantyPeriodMonths,
        @NotBlank String coverageConditions,
        @NotNull WarrantyPolicyStatus status) {
}
