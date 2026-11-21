package com.aquasuite.ims.service.audit.impl;

import com.aquasuite.ims.service.audit.AuditLogService;


import com.aquasuite.ims.dto.audit.response.AuditLogResponse;
import com.aquasuite.ims.mapper.audit.AuditLogMapper;
import com.aquasuite.ims.repository.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {
    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    @Override
    public List<AuditLogResponse> findByEntity(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, Pageable.unpaged())
                .map(mapper::toResponse)
                .getContent();
    }
}
