package com.aquasuite.ims.module.iam.internal.payload;

import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordRequest(
        @NotBlank(message = "Identifier cannot be blank") String identifier,
        @NotNull(message = "Recovery channel must be specified") RecoveryChannel channel) {}
