package com.aquasuite.ims.module.audit.internal.controller;

import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.audit.internal.entity.AuditLog;
import com.aquasuite.ims.module.audit.internal.mapper.AuditLogMapper;
import com.aquasuite.ims.module.audit.internal.repository.AuditLogRepository;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock AuditLogRepository repository;
    @Mock AuditLogMapper auditLogMapper;
    @Mock CatalogApi catalogApi;
    @Mock IamApi iamApi;
    @InjectMocks AuditLogController controller;

    @BeforeEach
    void authenticateAsOwner() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "owner", "n/a", List.of(new SimpleGrantedAuthority("ROLE_SHOP_OWNER"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsRecentActivityForAnEntityTypeAndEnrichesActorName() {
        UUID actorId = UUID.randomUUID();
        AuditLog audit = audit(actorId);
        AuditLogResponse response = AuditLogResponse.builder().id(audit.getAuditId()).actorId(actorId).action("CREATED").build();
        PageRequest pageable = PageRequest.of(0, 5);
        when(repository.findByEntityTypeOrderByCreatedAtDesc("USER_ACCOUNT", pageable))
                .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));
        when(auditLogMapper.toResponse(audit)).thenReturn(response);
        when(iamApi.getUserById(actorId)).thenReturn(UserSummaryDto.builder().id(actorId).username("owner").fullName("Store Owner").build());

        var result = controller.findByEntity("user_account", null, null, pageable).getData();

        assertThat(result.getContent()).singleElement().satisfies(log -> assertThat(log.getActorName()).isEqualTo("Store Owner"));
    }

    @Test
    void scopesVariantActivityToTheSelectedProductThroughCatalogApi() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 5);
        when(catalogApi.getVariantIdsByProductId(productId)).thenReturn(List.of(variantId));
        when(repository.findByEntityTypeAndEntityIdInOrderByCreatedAtDesc(eq("PRODUCT_VARIANT"), eq(List.of(variantId)), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = controller.findByEntity("PRODUCT_VARIANT", null, productId, pageable).getData();

        assertThat(result).isEmpty();
        verify(catalogApi).getVariantIdsByProductId(productId);
        verify(repository).findByEntityTypeAndEntityIdInOrderByCreatedAtDesc("PRODUCT_VARIANT", List.of(variantId), pageable);
    }

    @Test
    void rejectsAmbiguousOrInvalidProductFilters() {
        assertThatThrownBy(() -> controller.findByEntity("USER_ACCOUNT", UUID.randomUUID(), UUID.randomUUID(), PageRequest.of(0, 5)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> controller.findByEntity("USER_ACCOUNT", null, UUID.randomUUID(), PageRequest.of(0, 5)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void accountantCanReadCustomerActivityButNotAccountActivity() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "accountant", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        PageRequest pageable = PageRequest.of(0, 5);
        when(repository.findByEntityTypeOrderByCreatedAtDesc("CUSTOMER", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(controller.findByEntity("CUSTOMER", null, null, pageable).getData()).isEmpty();
        assertThatThrownBy(() -> controller.findByEntity("USER_ACCOUNT", null, null, pageable))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private AuditLog audit(UUID actorId) {
        AuditLog audit = new AuditLog();
        audit.setAuditId(UUID.randomUUID());
        audit.setUserId(actorId);
        audit.setEntityId(UUID.randomUUID());
        audit.setEntityType("USER_ACCOUNT");
        audit.setAction("CREATED");
        audit.setCreatedAt(Instant.now());
        return audit;
    }
}
