package com.aquasuite.ims.module.iam.internal.payload.response;

import java.util.UUID;

/** Pending registration reference; the reserved ID becomes a User ID only after verification. */
public record RegisterResponse(UUID id, String email, String status) {}
