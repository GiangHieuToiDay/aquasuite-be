package com.aquasuite.ims.module.iam.internal.payload;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password cannot be blank") String currentPassword,
        @NotBlank(message = "New password cannot be blank") String newPassword,
        @NotBlank(message = "Password confirmation cannot be blank") String confirmNewPassword) {}
