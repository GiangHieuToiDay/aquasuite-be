package com.aquasuite.ims.listener.audit;

import com.aquasuite.ims.entity.audit.AuditLog;
import com.aquasuite.ims.repository.audit.AuditLogRepository;
import com.aquasuite.ims.event.servicedesk.WarrantyTicketAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Ghi nhận audit log cho thao tác tạo/hủy phiếu yêu cầu bảo hành (UC-47).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WarrantyTicketAuditListener {

    private final AuditLogRepository auditLogRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWarrantyTicketAudited(WarrantyTicketAuditEvent event) {
        try {
            if (auditLogRepository.existsById(event.eventId())) {
                return;
            }
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.actorId());
            audit.setEntityType("WARRANTY_TICKET");
            audit.setEntityId(event.ticketId());
            audit.setAction(event.action());
            audit.setOldData(event.oldData());
            audit.setNewData(event.newData());
            audit.setCreatedAt(event.occurredAt() != null ? event.occurredAt().toInstant() : Instant.now());
            auditLogRepository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after warranty ticket eventId={}", event.eventId(), exception);
        }
    }
}

