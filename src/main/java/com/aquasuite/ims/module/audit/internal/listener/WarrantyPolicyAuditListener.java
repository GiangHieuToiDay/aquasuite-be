package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.servicedesk.api.event.WarrantyPolicyChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Persists UC-44 audit data only after the policy transaction has committed. */
@Component
@Slf4j
@RequiredArgsConstructor
public class WarrantyPolicyAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWarrantyPolicyChanged(WarrantyPolicyChangedEvent event) {
        try {
            if (repository.existsById(event.eventId())) return;
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.actorId());
            audit.setEntityType("WARRANTY_POLICY");
            audit.setEntityId(event.warrantyPolicyId());
            audit.setAction(event.action());
            audit.setOldData(event.oldData());
            audit.setNewData(event.newData());
            audit.setCreatedAt(event.occurredAt());
            repository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after warranty-policy eventId={}", event.eventId(), exception);
        }
    }
}
