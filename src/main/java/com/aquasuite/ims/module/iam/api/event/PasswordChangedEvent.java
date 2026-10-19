package com.aquasuite.ims.module.iam.api.event;

public record PasswordChangedEvent(
    Long userId,
    String reason
) {}
