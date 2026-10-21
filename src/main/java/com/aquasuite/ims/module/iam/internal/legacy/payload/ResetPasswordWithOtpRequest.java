package com.aquasuite.ims.module.iam.internal.legacy.payload;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordWithOtpRequest(
    @NotBlank(message = "Identifier cannot be blank") String identifier,
    @NotBlank(message = "OTP code cannot be blank") String otpCode,
    @NotBlank(message = "New password cannot be blank") String newPassword,
    @NotBlank(message = "Password confirmation cannot be blank") String confirmNewPassword
) {}
