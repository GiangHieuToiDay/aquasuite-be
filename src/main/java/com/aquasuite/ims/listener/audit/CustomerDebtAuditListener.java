package com.aquasuite.ims.listener.audit;

import com.aquasuite.ims.entity.audit.AuditLog;
import com.aquasuite.ims.repository.audit.AuditLogRepository;
import com.aquasuite.ims.event.finance.CustomerDebtAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerDebtAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCustomerDebtAction(CustomerDebtAuditEvent event) {
        try {
            if (repository.existsById(event.eventId())) {
                return;
            }
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.actorId());
            audit.setEntityType(event.entityType());
            audit.setEntityId(event.entityId());
            audit.setAction(event.action());
            audit.setOldData(event.oldData());
            audit.setNewData(event.newData());
            audit.setReason(event.reason());
            audit.setCreatedAt(event.occurredAt());
            repository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after CustomerDebt eventId={}", event.eventId(), exception);
        }
    }
}
