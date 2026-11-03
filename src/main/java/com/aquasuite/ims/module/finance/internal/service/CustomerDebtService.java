package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtCallResultRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtReminderRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.ReasonRequest;
import com.aquasuite.ims.module.finance.internal.pdf.StatementDocument;
import com.aquasuite.ims.module.finance.internal.payload.response.*;

import java.util.UUID;

public interface CustomerDebtService {
    CustomerDebtPageResponse list(String keyword, String customerType, String debtStatus, int page, int size);

    DebtDashboardStatsResponse stats();

    CustomerDebtDetailResponse detail(UUID customerId);

    BlacklistStatusResponse blacklistStatus(UUID customerId);

    CreditCheckResponse checkCredit(UUID customerId, CreditCheckRequest request);

    void approveOverride(UUID orderId, ReasonRequest request, UUID actorId);

    DebtReminderResponse sendReminder(UUID customerId, DebtReminderRequest request, UUID actorId);

    void saveCallResult(UUID customerId, DebtCallResultRequest request, UUID actorId);

    StatementDocument exportStatement(UUID customerId, UUID actorId);

    StatementEmailResponse emailStatement(UUID customerId, UUID actorId);

    void block(UUID customerId, ReasonRequest request, UUID actorId);

    void restore(UUID customerId, ReasonRequest request, UUID actorId);
}
