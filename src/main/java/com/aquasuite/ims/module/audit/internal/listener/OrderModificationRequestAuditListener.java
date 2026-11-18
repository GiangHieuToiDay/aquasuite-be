package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.sales.api.event.OrderModificationRequestReviewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persists the immutable review trail emitted by UC-26 after its transaction commits. */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderModificationRequestAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReviewed(OrderModificationRequestReviewedEvent event) {
        try {
            if (repository.existsById(event.eventId())) return;
            AuditLog log = new AuditLog();
            log.setAuditId(event.eventId());
            log.setUserId(event.actorId());
            log.setEntityType("ORDER_MODIFICATION_REQUEST");
            log.setEntityId(event.requestId());
            log.setAction(event.action());
            log.setOldData(event.oldData());
            log.setNewData(event.newData());
            log.setReason(event.reason());
            log.setCreatedAt(event.occurredAt());
            repository.save(log);
        } catch (RuntimeException exception) {
            log.error("Could not persist audit event {} for UC-26", event.eventId(), exception);
        }
    }
}
