package com.aquasuite.ims.module.iam.api;

import java.util.UUID;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;

/** Public synchronous identity contract for IAM consumers. */
public interface IamApi {

    UserSummaryDto getCurrentUser();

    UUID getCurrentUserId();
}
