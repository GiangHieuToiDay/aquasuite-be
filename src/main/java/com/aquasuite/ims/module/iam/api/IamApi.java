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
}
