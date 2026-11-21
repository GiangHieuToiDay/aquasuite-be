package com.aquasuite.ims.dto.finance.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

/** Idempotent allocation batch submitted by the Accountant UI. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatePaymentAllocationRequest {
    @NotNull(message = "Allocation request ID is required")
    UUID allocationRequestId;

    @NotEmpty(message = "At least one invoice allocation is required")
    List<@Valid PaymentAllocationLineRequest> allocations;

    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    String reason;
}
