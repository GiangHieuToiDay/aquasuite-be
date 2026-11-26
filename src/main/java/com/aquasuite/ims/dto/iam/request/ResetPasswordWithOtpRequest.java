package com.aquasuite.ims.dto.iam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordWithOtpRequest(
        @NotBlank @Size(max = 320) String identifier,
        @NotBlank @Size(max = 20) String otpCode,
        @NotBlank @Size(max = 72) String newPassword,
        @NotBlank @Size(max = 72) String confirmNewPassword) {}
