package com.aquasuite.ims.module.iam.api.dto;


import java.util.UUID;

/** Public, entity-free view of the authenticated IAM user. */
public record UserSummaryDto(
        UUID id,
        String username,
        String email,
        String phone,
        String avatarUrl,
        String status
) {
}