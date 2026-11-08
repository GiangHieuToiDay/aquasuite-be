package com.aquasuite.ims.module.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Provisional UC-29 contract. Implementations own payment verification and deduplication. */
public interface ProjectDepositPaymentApi {
    Payment recordDeposit(DepositCommand command);
    List<Payment> getPayments(UUID orderId);

    enum Method { CASH, BANK_TRANSFER }
    enum Status { PENDING, VERIFIED, REJECTED }

    record DepositCommand(UUID requestId, UUID orderId, UUID customerId, BigDecimal amount,
                          Method method, String referenceNo, String proofUrl, UUID actorId) {}

    record Payment(UUID paymentId, UUID requestId, UUID orderId, BigDecimal amount, Method method,
                   Status status, Instant recordedAt) {}
}
