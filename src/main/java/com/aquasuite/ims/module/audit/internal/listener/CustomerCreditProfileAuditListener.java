package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerCreditProfileAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdated(CustomerCreditProfileUpdatedEvent event) {
        try {
            if (repository.existsById(event.eventId())) return;
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.approvedBy());
            audit.setEntityType("CUSTOMER");
            audit.setEntityId(event.customerId());
            audit.setAction("CUSTOMER_CREDIT_PROFILE_UPDATED");
            Map<String, Object> oldData = new HashMap<>();
            oldData.put("creditLimit", event.previousCreditLimit());
            oldData.put("creditTermDays", event.previousCreditTermDays());
            Map<String, Object> newData = new HashMap<>();
            newData.put("creditLimit", event.creditLimit());
            newData.put("creditTermDays", event.creditTermDays());
            newData.put("requestedBy", event.requestedBy());
            newData.put("approvedBy", event.approvedBy());
            audit.setOldData(oldData);
            audit.setNewData(newData);
            audit.setReason(event.reason());
            audit.setCreatedAt(event.occurredAt());
            repository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after customer credit profile eventId={}", event.eventId(), exception);
        }
    }
}
