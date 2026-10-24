package com.aquasuite.ims.module.iam.api;

import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;

import java.util.UUID;

/** Public synchronous identity contract for IAM consumers. */
public interface IamApi {

    UserSummaryDto getCurrentUser();

    UUID getCurrentUserId();

    boolean isAdmin();

    boolean isSalesStaffOrAdmin();

    void validateAdminAccess();

    UserSummaryDto getUserById(UUID userId);

    java.util.Optional<UserSummaryDto> findByEmail(String email);

    boolean existsByPhoneAndUserIdNot(String phone, UUID userId);

    void updateUserProfile(UUID userId, String fullName, String phone, String avatarUrl);
}
