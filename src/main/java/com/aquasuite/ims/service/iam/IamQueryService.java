package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.response.UserSummaryDto;

import java.util.Optional;
import java.util.UUID;

public interface IamQueryService {

    UserSummaryDto getCurrentUser();

    UUID getCurrentUserId();

    UserSummaryDto getUserById(UUID userId);

    Optional<UserSummaryDto> findByEmail(String email);

    boolean existsByPhoneAndUserIdNot(String phone, UUID userId);

    void updateUserProfile(UUID userId, String fullName, String phone, String avatarUrl);
}
