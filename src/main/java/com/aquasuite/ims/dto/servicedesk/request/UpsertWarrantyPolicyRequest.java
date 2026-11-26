package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import jakarta.validation.constraints.Max;
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
        @NotNull @Positive @Max(value = 600, message = "Thời gian bảo hành tối đa 600 tháng") Integer warrantyPeriodMonths,
        @NotBlank @Size(max = 4000, message = "Điều kiện bảo hành tối đa 4000 ký tự") String coverageConditions,
        @NotNull WarrantyPolicyStatus status) {
}
