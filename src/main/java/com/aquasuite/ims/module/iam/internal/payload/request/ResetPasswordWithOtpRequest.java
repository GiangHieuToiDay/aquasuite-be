package com.aquasuite.ims.module.iam.internal.payload.request;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordWithOtpRequest(@NotBlank String identifier, @NotBlank String otpCode,
                                          @NotBlank String newPassword, @NotBlank String confirmNewPassword) {}
