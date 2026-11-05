package com.aquasuite.ims.module.iam.api;

import com.aquasuite.ims.module.iam.api.dto.CreditApprovalGrantDto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One-time approval-grant contract for sensitive credit-profile changes made from a POS session.
 * The caller remains the Sales Staff; the returned approver is an independently re-authenticated Shop Owner.
 */
public interface CreditApprovalGrantApi {

    CreditApprovalGrantDto consumeCreditProfileGrant(
            UUID requesterId,
            String grantToken,
            UUID customerId,
            BigDecimal creditLimit,
            Integer creditTermDays);
}
