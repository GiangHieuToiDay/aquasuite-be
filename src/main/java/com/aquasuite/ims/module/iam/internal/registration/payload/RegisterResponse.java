package com.aquasuite.ims.module.iam.internal.registration.payload;

import java.util.UUID;

/** Safe registration result, never a User entity or credential. */
public record RegisterResponse(UUID id, String email, String status) {}
