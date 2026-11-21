package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.request.ChangeUserStatusRequest;
import com.aquasuite.ims.dto.iam.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.dto.iam.request.CreateUserAccountRequest;
import com.aquasuite.ims.dto.iam.request.UpdateUserAccountRequest;
import com.aquasuite.ims.dto.iam.response.AccountPageResponse;
import com.aquasuite.ims.dto.iam.response.AccountStatsResponse;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantDto;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantResponse;
import com.aquasuite.ims.dto.iam.response.UserAccountResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service quản lý tài khoản người dùng nội bộ và cấp duyệt tín dụng hợp nhất.
 */
public interface UserAccountService {
    AccountStatsResponse stats();
    AccountPageResponse search(String keyword, UUID roleId, String status, Pageable pageable, UUID currentUserId);
    UserAccountResponse detail(UUID id, UUID currentUserId);
    UserAccountResponse create(CreateUserAccountRequest request, UUID actorId);
    UserAccountResponse update(UUID id, UpdateUserAccountRequest request, UUID actorId);
    UserAccountResponse changeStatus(UUID id, ChangeUserStatusRequest request, UUID actorId);

    CreditApprovalGrantDto consumeCreditProfileGrant(
            UUID requesterId,
            String grantToken,
            UUID customerId,
            BigDecimal creditLimit,
            Integer creditTermDays);

    CreditApprovalGrantResponse issue(UUID requesterId, CreateCreditApprovalGrantRequest request);
}
