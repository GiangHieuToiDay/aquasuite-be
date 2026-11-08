package com.aquasuite.ims.module.iam.internal.service;

import java.util.UUID;

/** Registration data held only until email verification; never log credentials. */
public record PendingRegistration(
        UUID id, String fullName, String email, String phone, String passwordHash) {
    @Override
    public String toString() {
        return "PendingRegistration[redacted]";
    }
}
