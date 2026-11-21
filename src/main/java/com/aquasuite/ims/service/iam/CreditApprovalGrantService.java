package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantResponse;

import java.util.UUID;

public interface CreditApprovalGrantService {
    com.aquasuite.ims.dto.iam.response.CreditApprovalGrantDto consumeCreditProfileGrant(
            UUID requesterId,
            String grantToken,
            UUID customerId,
            java.math.BigDecimal creditLimit,
            Integer creditTermDays);

    CreditApprovalGrantResponse issue(UUID requesterId, CreateCreditApprovalGrantRequest request);
}
