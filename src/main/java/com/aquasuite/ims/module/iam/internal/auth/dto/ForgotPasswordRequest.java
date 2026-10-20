package com.aquasuite.ims.module.iam.internal.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordRequest(@NotBlank String identifier, @NotNull RecoveryChannel channel) {}
