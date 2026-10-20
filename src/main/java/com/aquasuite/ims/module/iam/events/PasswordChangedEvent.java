package com.aquasuite.ims.module.iam.events;

public record PasswordChangedEvent(
    java.util.UUID userId,
    String reason
) {}
