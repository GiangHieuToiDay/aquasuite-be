package com.aquasuite.ims.module.iam.internal.legacy;

import com.aquasuite.ims.module.iam.internal.legacy.dto.UserSummaryDto;

/** Preserved legacy contract; excluded from the runtime component scan. */
public interface IamFacade {
    UserSummaryDto getCurrentUser();
    Long getCurrentUserId();
}
