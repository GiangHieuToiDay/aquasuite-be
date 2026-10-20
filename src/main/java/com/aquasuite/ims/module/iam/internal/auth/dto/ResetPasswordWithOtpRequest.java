package com.aquasuite.ims.module.iam.internal.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordWithOtpRequest(@NotBlank String identifier, @NotBlank String otpCode,
                                          @NotBlank String newPassword, @NotBlank String confirmNewPassword) {}
