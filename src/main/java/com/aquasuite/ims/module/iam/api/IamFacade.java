package com.aquasuite.ims.module.iam.api;

import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import java.util.UUID;

/** Current authenticated user. Missing/invalid JWT context raises AUTH_001; never returns a fallback user. */
public interface IamFacade {
    UserSummaryDto getCurrentUser();

    UUID getCurrentUserId();
}
