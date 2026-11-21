package com.aquasuite.ims.dto.iam.request;
import com.aquasuite.ims.enums.iam.RecoveryChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordRequest(@NotBlank String identifier, @NotNull RecoveryChannel channel) {}
