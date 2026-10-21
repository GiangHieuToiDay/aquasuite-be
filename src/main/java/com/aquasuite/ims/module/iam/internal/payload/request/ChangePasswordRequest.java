package com.aquasuite.ims.module.iam.internal.payload.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword,
                                    @NotBlank String confirmNewPassword) {}
