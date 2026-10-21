package com.aquasuite.ims.module.iam.internal.legacy.event;

public record PasswordChangedEvent(Long userId, String reason) {}
