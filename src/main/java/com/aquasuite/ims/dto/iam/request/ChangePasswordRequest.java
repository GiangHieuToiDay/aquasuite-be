package com.aquasuite.ims.dto.iam.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 100) String currentPassword,
        @NotBlank @Size(max = 72) String newPassword,
        @NotBlank @Size(max = 72) String confirmNewPassword) {}
