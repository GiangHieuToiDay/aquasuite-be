package com.aquasuite.ims.module.iam.internal.payload.request;

import jakarta.validation.constraints.*;

public record VerifyRegistrationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {
    public VerifyRegistrationRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "VerifyRegistrationRequest[redacted]";
    }
}
