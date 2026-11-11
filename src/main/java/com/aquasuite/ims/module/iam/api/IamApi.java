package com.aquasuite.ims.module.iam.api;

import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;

import java.util.UUID;

/** Public synchronous identity contract for IAM consumers. */
public interface IamApi {

    UserSummaryDto getCurrentUser();

    UUID getCurrentUserId();

    UserSummaryDto getUserById(UUID userId);

    /** Display names for known IDs only; missing users are omitted. */
    java.util.Map<UUID, String> findUserDisplayNames(java.util.Set<UUID> userIds);

    java.util.Optional<UserSummaryDto> findByEmail(String email);

    boolean existsByPhoneAndUserIdNot(String phone, UUID userId);

    void updateUserProfile(UUID userId, String fullName, String phone, String avatarUrl);
}
