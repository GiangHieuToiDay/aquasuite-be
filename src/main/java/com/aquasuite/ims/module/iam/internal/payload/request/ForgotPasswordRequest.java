package com.aquasuite.ims.module.iam.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordRequest(@NotBlank String identifier, @NotNull RecoveryChannel channel) {}
