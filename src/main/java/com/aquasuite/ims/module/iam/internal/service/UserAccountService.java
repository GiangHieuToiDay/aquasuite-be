package com.aquasuite.ims.module.iam.internal.service;


import com.aquasuite.ims.module.iam.internal.payload.request.ChangeUserStatusRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.AccountPageResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.AccountStatsResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.UserAccountResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserAccountService {
    AccountStatsResponse stats();
    AccountPageResponse search(String keyword, UUID roleId, String status, Pageable pageable, UUID currentUserId);
    UserAccountResponse detail(UUID id, UUID currentUserId);
    UserAccountResponse create(CreateUserAccountRequest request, UUID actorId);
    UserAccountResponse update(UUID id, UpdateUserAccountRequest request, UUID actorId);
    UserAccountResponse changeStatus(UUID id, ChangeUserStatusRequest request, UUID actorId);
}
