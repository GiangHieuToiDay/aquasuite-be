package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.servicedesk.api.event.WarrantyRecordAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Ghi nhận audit log cho thao tác tạo/hủy thẻ bảo hành điện tử (UC-45).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WarrantyRecordAuditListener {

    private final AuditLogRepository auditLogRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWarrantyRecordAudited(WarrantyRecordAuditEvent event) {
        try {
            if (auditLogRepository.existsById(event.eventId())) {
                return;
            }
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.actorId());
            audit.setEntityType("WARRANTY_RECORD");
            audit.setEntityId(event.warrantyRecordId());
            audit.setAction(event.action());
            audit.setOldData(event.oldData());
            audit.setNewData(event.newData());
            audit.setCreatedAt(event.occurredAt() != null ? event.occurredAt().toInstant() : Instant.now());
            auditLogRepository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after warranty eventId={}", event.eventId(), exception);
        }
    }
}
