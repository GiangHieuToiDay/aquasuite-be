package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.iam.api.event.UserAccountCreatedEvent;
import com.aquasuite.ims.module.iam.api.event.UserAccountStatusChangedEvent;
import com.aquasuite.ims.module.iam.api.event.UserAccountUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class IamAccountAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(UserAccountCreatedEvent event) {
        persistSafely(event.eventId(), event.actorId(), event.userId(), "USER_ACCOUNT", "CREATED", Map.of(), event.data(), null, event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdated(UserAccountUpdatedEvent event) {
        persistSafely(event.eventId(), event.actorId(), event.userId(), "USER_ACCOUNT", "UPDATED", event.oldData(), event.newData(), null, event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(UserAccountStatusChangedEvent event) {
        persistSafely(event.eventId(), event.actorId(), event.userId(), "USER_ACCOUNT", "STATUS_CHANGED",
                Map.of("status", event.oldStatus()), Map.of("status", event.newStatus()), event.reason(), event.occurredAt());
    }

    private void persistSafely(UUID eventId, UUID actorId, UUID entityId, String entityType, String action,
                               Map<String, Object> oldData, Map<String, Object> newData, String reason, Instant occurredAt) {
        try {
            save(eventId, actorId, entityId, entityType, action, oldData, newData, reason, occurredAt);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after IAM eventId={}; the source account transaction remains committed.", eventId, exception);
        }
    }

    private void save(UUID eventId, UUID actorId, UUID entityId, String entityType, String action,
                      Map<String, Object> oldData, Map<String, Object> newData, String reason, Instant occurredAt) {
        if (repository.existsById(eventId)) return;
        AuditLog log = new AuditLog();
        log.setAuditId(eventId); log.setUserId(actorId); log.setEntityId(entityId); log.setEntityType(entityType);
        log.setAction(action); log.setOldData(oldData); log.setNewData(newData); log.setReason(reason); log.setCreatedAt(occurredAt);
        repository.save(log);
    }
}
