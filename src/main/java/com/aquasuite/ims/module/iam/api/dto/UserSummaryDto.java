package com.aquasuite.ims.module.iam.api.dto;

import lombok.Builder;
import java.util.UUID;

/** Public, entity-free view of the authenticated IAM user. */
@Builder
public record UserSummaryDto(
        UUID id,
        String username,
        String email,
        String phone,
        String avatarUrl,
        String status
) {
}
