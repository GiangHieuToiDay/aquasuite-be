package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerCreditProfileRequestAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRequestEvent(CustomerCreditProfileRequestEvent event) {
        try {
            if (repository.existsById(event.eventId())) return;
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.actorId());
            // UC-24 reads customer activity through entity_type=CUSTOMER/entity_id=customerId.
            audit.setEntityType("CUSTOMER");
            audit.setEntityId(event.customerId());
            audit.setAction("CREDIT_PROFILE_REQUEST_" + event.action());
            audit.setNewData(Map.of(
                    "creditProfileRequestId", event.requestId(),
                    "customerId", event.customerId(),
                    "requestedCreditLimit", event.requestedCreditLimit(),
                    "requestedCreditTermDays", event.requestedCreditTermDays(),
                    "identityEvidenceProvided", event.identityEvidenceProvided()));
            audit.setReason("Customer credit profile request " + event.action().toLowerCase() + ".");
            audit.setCreatedAt(event.occurredAt());
            repository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after credit profile request eventId={}", event.eventId(), exception);
        }
    }
}
