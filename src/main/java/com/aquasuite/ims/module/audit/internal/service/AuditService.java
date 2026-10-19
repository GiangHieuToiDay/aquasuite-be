package com.aquasuite.ims.module.audit.internal.service;

import com.aquasuite.ims.module.audit.api.AuditApi;
import com.aquasuite.ims.module.audit.api.dto.AuditCommand;
import com.aquasuite.ims.module.audit.internal.entity.AuditEntry;
import com.aquasuite.ims.module.audit.internal.repository.AuditEntryRepository;
import com.aquasuite.ims.share.exception.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService implements AuditApi {
    private final AuditEntryRepository entries;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(AuditCommand c) {
        if (c == null
                || c.actorId() == null
                || c.action() == null
                || c.action().isBlank()
                || c.aggregateId() == null
                || c.occurredAt() == null) throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        var e = new AuditEntry();
        e.setId(UUID.randomUUID());
        e.setActorId(c.actorId());
        e.setAction(c.action());
        e.setAggregateType("BUNDLE");
        e.setAggregateId(c.aggregateId());
        e.setBefore(c.before());
        e.setAfter(c.after());
        e.setOccurredAt(c.occurredAt());
        entries.saveAndFlush(e);
        return e.getId();
    }
}
