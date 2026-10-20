package com.aquasuite.ims.module.iam.internal.registration.payload;

import com.aquasuite.ims.module.iam.internal.registration.validation.ValidRegistration;
import jakarta.validation.constraints.*;

/** Registration form only; credentials are deliberately excluded from toString. */
@ValidRegistration
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 50) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "0[35789][0-9]{8}") String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String passwordConfirmation,
        @NotNull @AssertTrue Boolean termsAccepted) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
        phone = phone == null ? null : phone.trim();
    }

    @Override public String toString() { return "RegisterRequest[redacted]"; }
}
