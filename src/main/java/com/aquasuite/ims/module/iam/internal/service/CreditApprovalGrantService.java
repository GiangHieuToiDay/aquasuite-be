package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.payload.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.CreditApprovalGrantResponse;

import java.util.UUID;

public interface CreditApprovalGrantService {
    CreditApprovalGrantResponse issue(UUID requesterId, CreateCreditApprovalGrantRequest request);
}
