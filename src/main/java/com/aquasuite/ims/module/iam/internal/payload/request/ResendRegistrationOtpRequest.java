package com.aquasuite.ims.module.iam.internal.payload.request;

import jakarta.validation.constraints.*;

public record ResendRegistrationOtpRequest(@NotBlank @Email @Size(max = 320) String email) {
    public ResendRegistrationOtpRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "ResendRegistrationOtpRequest[redacted]";
    }
}
