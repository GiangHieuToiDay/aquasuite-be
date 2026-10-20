package com.aquasuite.ims.module.crm.api.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CustomerRegistrationRequest(
        @NotNull UUID userId,
        @NotBlank @Size(min=2,max=50) String fullName,
        @NotBlank @Pattern(regexp="0[35789][0-9]{8}") String phone,
        @NotBlank @Email @Size(max=320) String email) {
    @Override public String toString() { return "CustomerRegistrationRequest[redacted]"; }
}
