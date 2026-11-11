package com.aquasuite.ims.module.finance.internal.dev;

import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** In-memory simulator, never posts to transactions or changes any real customer balance. */
@Service
@Profile("uc29-demo & !prod & !production")
public class DemoProjectDepositPaymentApi implements ProjectDepositPaymentApi {
    private final Map<UUID, DepositCommand> commands = new LinkedHashMap<>();
    private final Map<UUID, Payment> payments = new LinkedHashMap<>();
    private final Map<UUID, UUID> requestToPayment = new HashMap<>();
    private final Set<String> bankReferences = new HashSet<>();

    @Override
    public synchronized Payment recordDeposit(DepositCommand command) {
        DepositCommand existing = commands.get(command.requestId());
        if (existing != null) {
            if (!existing.equals(command)) throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            return payments.get(requestToPayment.get(command.requestId()));
        }
        if (command.amount() == null || command.amount().signum() <= 0 || command.method() == null
                || command.orderId() == null || command.requestId() == null) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        String reference = command.referenceNo() == null ? "" : command.referenceNo().strip();
        if (command.method() == Method.BANK_TRANSFER && (reference.isEmpty() || bankReferences.contains(reference))) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        // Even CASH remains PENDING in the simulator until an explicit demo verification.
        Payment payment = new Payment(UUID.randomUUID(), command.requestId(), command.orderId(), command.amount(), command.method(), Status.PENDING, Instant.now());
        commands.put(command.requestId(), command);
        payments.put(payment.paymentId(), payment);
        requestToPayment.put(command.requestId(), payment.paymentId());
        if (command.method() == Method.BANK_TRANSFER) bankReferences.add(reference);
        return payment;
    }

    @Override
    public synchronized List<Payment> getPayments(UUID orderId) {
        return payments.values().stream().filter(p -> p.orderId().equals(orderId)).toList();
    }

    public synchronized Payment verify(UUID paymentId, Status status) {
        Payment old = payments.get(paymentId);
        if (old == null) throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        if (status == null || status == Status.PENDING) throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        if (old.status() != Status.PENDING && old.status() != status) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        Payment updated = new Payment(old.paymentId(), old.requestId(), old.orderId(), old.amount(), old.method(), status, old.recordedAt());
        payments.put(paymentId, updated);
        return updated;
    }
}
