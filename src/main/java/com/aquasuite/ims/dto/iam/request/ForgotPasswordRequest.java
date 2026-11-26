package com.aquasuite.ims.dto.iam.request;
import com.aquasuite.ims.enums.iam.RecoveryChannel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(@NotBlank @Size(max = 320) String identifier, @NotNull RecoveryChannel channel) {}
