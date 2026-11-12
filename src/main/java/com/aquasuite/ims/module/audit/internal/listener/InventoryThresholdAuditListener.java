package com.aquasuite.ims.module.audit.internal.listener;

import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.inventory.api.event.InventoryThresholdChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Ghi audit khi ngưỡng tồn an toàn của một SKU được cấu hình lại trong UC-31.
 * Lưu lại người thực hiện cùng giá trị min/max trước và sau để phục vụ truy vết (GB-06/GB-26).
 * Chạy sau khi giao dịch gốc commit và trong transaction riêng để lỗi audit không ảnh hưởng nghiệp vụ chính.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryThresholdAuditListener {
    private final AuditLogRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onThresholdChanged(InventoryThresholdChangedEvent event) {
        try {
            if (repository.existsById(event.eventId())) return;
            AuditLog audit = new AuditLog();
            audit.setAuditId(event.eventId());
            audit.setUserId(event.actorId());
            audit.setEntityType("INVENTORY_THRESHOLD");
            audit.setEntityId(event.variantId());
            audit.setAction("UPDATE_THRESHOLD");
            audit.setOldData(snapshot(event.oldMinimumQty(), event.oldMaximumQty()));
            audit.setNewData(snapshot(event.newMinimumQty(), event.newMaximumQty()));
            audit.setCreatedAt(event.occurredAt());
            repository.save(audit);
        } catch (RuntimeException exception) {
            log.error("Audit persistence failed after inventory threshold eventId={}", event.eventId(), exception);
        }
    }

    private Map<String, Object> snapshot(Object minimumQty, Object maximumQty) {
        Map<String, Object> data = new HashMap<>();
        data.put("minimumQty", minimumQty);
        data.put("maximumQty", maximumQty);
        return data;
    }
}
