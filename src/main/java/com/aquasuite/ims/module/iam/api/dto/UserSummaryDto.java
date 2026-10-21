package com.aquasuite.ims.module.iam.api.dto;

import lombok.Builder;
import java.util.List;
import java.util.UUID;

/** Public, entity-free view of the authenticated IAM user. */
@Builder
public record UserSummaryDto(
        UUID id,
        String username,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String status,
        List<String> roles
) {
    public UserSummaryDto(UUID id, String username, String email, String phone, String avatarUrl, String status) {
        this(id, username, null, email, phone, avatarUrl, status, List.of());
    }
}
