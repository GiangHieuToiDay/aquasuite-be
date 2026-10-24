package com.aquasuite.ims.module.iam.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * REST payload for {@code GET /api/v1/auth/me}. Exposes only the identity fields and normalized
 * active role names of the authenticated subject; never tokens, session data or permissions.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    private UUID id;
    private String username;
    private String email;

    @Builder.Default private List<String> roles = List.of();
}
