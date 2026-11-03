package com.aquasuite.ims.module.audit.internal.service;

import com.aquasuite.ims.module.audit.api.AuditQueryApi;
import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.audit.internal.mapper.AuditLogMapper;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditQueryApiImpl implements AuditQueryApi {
    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    @Override
    public List<AuditLogResponse> findByEntity(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, Pageable.unpaged())
                .map(mapper::toResponse)
                .getContent();
    }
}
