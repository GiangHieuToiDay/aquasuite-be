package com.aquasuite.ims.module.audit.internal.controller;

import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.audit.internal.mapper.AuditLogMapper;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Locale;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SHOP_OWNER','ACCOUNTANT')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuditLogController {
    AuditLogRepository repository;
    AuditLogMapper auditLogMapper;
    CatalogApi catalogApi;
    IamApi iamApi;

    @GetMapping
    public ApiResponse<Page<AuditLogResponse>> findByEntity(
            @RequestParam String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID productId,
            Pageable pageable) {
        String normalizedEntityType = normalizeEntityType(entityType);
        assertEntityReadAllowed(normalizedEntityType);
        if (entityId != null && productId != null) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        Page<AuditLogResponse> page;
        if (entityId != null) {
            page = repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(normalizedEntityType, entityId, pageable)
                    .map(auditLogMapper::toResponse);
        } else if (productId != null) {
            if (!"PRODUCT_VARIANT".equals(normalizedEntityType)) {
                throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
            }
            List<UUID> variantIds = catalogApi.getVariantIdsByProductId(productId);
            page = variantIds.isEmpty()
                    ? Page.empty(pageable)
                    : repository.findByEntityTypeAndEntityIdInOrderByCreatedAtDesc(normalizedEntityType, variantIds, pageable)
                            .map(auditLogMapper::toResponse);
        } else {
            page = repository.findByEntityTypeOrderByCreatedAtDesc(normalizedEntityType, pageable)
                    .map(auditLogMapper::toResponse);
        }
        return ApiResponse.ok(page.map(this::enrichActorName));
    }

    private String normalizeEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        return entityType.trim().toUpperCase(Locale.ROOT);
    }

    /** ACCOUNTANT may read only UC-24 customer activity; all other audit domains remain owner-only. */
    private void assertEntityReadAllowed(String entityType) {
        if ("CUSTOMER".equals(entityType) || hasRole("SHOP_OWNER")) return;
        throw new AccessDeniedException("Only the Store Owner may read this audit entity type.");
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    private AuditLogResponse enrichActorName(AuditLogResponse response) {
        if (response.getActorId() == null) return response;
        try {
            UserSummaryDto actor = iamApi.getUserById(response.getActorId());
            String actorName = actor.fullName() == null || actor.fullName().isBlank()
                    ? actor.username()
                    : actor.fullName();
            response.setActorName(actorName);
        } catch (RuntimeException exception) {
            log.debug("Audit actor {} is not available for display", response.getActorId());
        }
        return response;
    }
}
