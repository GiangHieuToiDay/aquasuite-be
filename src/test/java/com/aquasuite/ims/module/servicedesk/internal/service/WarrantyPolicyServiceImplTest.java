package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBrandSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogCategorySummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyPolicy;
import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyPolicyStatus;
import com.aquasuite.ims.module.servicedesk.internal.mapper.WarrantyPolicyMapper;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyResponse;
import com.aquasuite.ims.module.servicedesk.internal.repository.WarrantyPolicyRepository;
import com.aquasuite.ims.module.servicedesk.internal.service.impl.WarrantyPolicyServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyPolicyServiceImplTest {

    @Mock WarrantyPolicyRepository warrantyPolicyRepository;
    @Mock WarrantyPolicyMapper warrantyPolicyMapper;
    @Mock CatalogApi catalogApi;
    @Mock IamApi iamApi;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks WarrantyPolicyServiceImpl service;

    @Test
    void createsPolicyAndPublishesPostCommitAuditEvent() {
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("TOTO").status("ACTIVE").build()));
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.of(CatalogCategorySummaryDto.builder().categoryId(categoryId).categoryName("Bồn cầu").status("ACTIVE").build()));
        when(warrantyPolicyRepository.existsByBrandIdAndCategoryIdAndStatus(brandId, categoryId, WarrantyPolicyStatus.ACTIVE)).thenReturn(false);
        when(warrantyPolicyRepository.nextPolicyCodeSequence()).thenReturn(1L);
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(warrantyPolicyRepository.saveAndFlush(any(WarrantyPolicy.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WarrantyPolicyResponse mapped = WarrantyPolicyResponse.builder().build();
        when(warrantyPolicyMapper.toResponse(any(WarrantyPolicy.class))).thenReturn(mapped);
        when(catalogApi.getVariantsByWarrantyPolicyId(any())).thenReturn(java.util.List.of());

        WarrantyPolicyResponse result = service.create(request(brandId, categoryId));

        ArgumentCaptor<WarrantyPolicy> policy = ArgumentCaptor.forClass(WarrantyPolicy.class);
        verify(warrantyPolicyRepository).saveAndFlush(policy.capture());
        assertThat(policy.getValue().getPolicyCode()).isEqualTo("POL-00001");
        assertThat(policy.getValue().getCreatedBy()).isEqualTo(actorId);
        assertThat(result.getBrandName()).isEqualTo("TOTO");
        verify(eventPublisher).publishEvent(any(com.aquasuite.ims.module.servicedesk.api.event.WarrantyPolicyChangedEvent.class));
    }

    @Test
    void blocksDeleteWhenAVariantReferencesThePolicy() {
        UUID policyId = UUID.randomUUID();
        WarrantyPolicy policy = WarrantyPolicy.builder().warrantyPolicyId(policyId).policyCode("POL-00001").build();
        when(warrantyPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(catalogApi.getVariantsByWarrantyPolicyId(policyId)).thenReturn(java.util.List.of(
                com.aquasuite.ims.module.catalog.api.dto.CatalogVariantWarrantyReferenceDto.builder().variantId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.delete(policyId)).isInstanceOf(AppException.class);

        verify(warrantyPolicyRepository, never()).delete(any());
    }

    private UpsertWarrantyPolicyRequest request(UUID brandId, UUID categoryId) {
        return UpsertWarrantyPolicyRequest.builder().policyName("Bảo hành sứ vệ sinh TOTO")
                .brandId(brandId).categoryId(categoryId).warrantyPeriodMonths(120)
                .coverageConditions("Bảo hành 10 năm phần sứ.").status(WarrantyPolicyStatus.ACTIVE).build();
    }
}
