package com.aquasuite.ims.module.iam.events;

public record PasswordChangedEvent(
    Long userId,
    String reason
) {}
