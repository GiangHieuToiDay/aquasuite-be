package com.aquasuite.ims.module.audit.internal.controller;

import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.audit.internal.mapper.AuditLogMapper;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STORE_OWNER')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogController {
    AuditLogRepository repository;
    AuditLogMapper auditLogMapper;

    @GetMapping
    public ApiResponse<Page<AuditLogResponse>> findByEntity(@RequestParam String entityType, @RequestParam UUID entityId, Pageable pageable) {
        return ApiResponse.ok(repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
                .map(auditLogMapper::toResponse));
    }
}
