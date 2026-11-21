package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogBrandSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogCategorySummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogVariantWarrantyReferenceDto;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.event.servicedesk.WarrantyPolicyChangedEvent;
import com.aquasuite.ims.entity.servicedesk.WarrantyPolicy;
import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.mapper.servicedesk.WarrantyPolicyMapper;
import com.aquasuite.ims.dto.servicedesk.request.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyReferenceDataResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyResponse;
import com.aquasuite.ims.repository.servicedesk.WarrantyPolicyRepository;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyPolicyServiceImpl;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarrantyPolicyServiceImplTest {

    @Mock WarrantyPolicyRepository warrantyPolicyRepository;
    @Mock WarrantyPolicyMapper warrantyPolicyMapper;
    @Mock CatalogLookupService catalogApi;
    @Mock IamQueryService iamApi;
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
        when(catalogApi.getVariantsByWarrantyPolicyId(any())).thenReturn(List.of());

        WarrantyPolicyResponse result = service.create(request(brandId, categoryId));

        ArgumentCaptor<WarrantyPolicy> policy = ArgumentCaptor.forClass(WarrantyPolicy.class);
        verify(warrantyPolicyRepository).saveAndFlush(policy.capture());
        assertThat(policy.getValue().getPolicyCode()).isEqualTo("POL-00001");
        assertThat(policy.getValue().getCreatedBy()).isEqualTo(actorId);
        assertThat(result.getBrandName()).isEqualTo("TOTO");
        assertThat(result.getCategoryName()).isEqualTo("Bồn cầu");
        assertThat(result.getVariantReferenceCount()).isZero();
        verify(eventPublisher).publishEvent(any(WarrantyPolicyChangedEvent.class));
    }

    @Test
    void rejectsDuplicateActivePolicyForSameBrandAndCategory() {
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        WarrantyPolicy existing = WarrantyPolicy.builder()
                .warrantyPolicyId(UUID.randomUUID())
                .brandId(brandId)
                .categoryId(categoryId)
                .policyName("Chính sách TOTO Sứ 10 năm")
                .status(WarrantyPolicyStatus.ACTIVE)
                .build();
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("TOTO").status("ACTIVE").build()));
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.of(CatalogCategorySummaryDto.builder().categoryId(categoryId).categoryName("Bồn cầu").status("ACTIVE").build()));
        when(warrantyPolicyRepository.existsByBrandIdAndCategoryIdAndStatus(brandId, categoryId, WarrantyPolicyStatus.ACTIVE)).thenReturn(true);
        when(warrantyPolicyRepository.search(eq(null), eq(WarrantyPolicyStatus.ACTIVE.name()), any()))
                .thenReturn(new PageImpl<>(List.of(existing)));

        assertThatThrownBy(() -> service.create(request(brandId, categoryId)))
                .isInstanceOf(AppException.class)
                .satisfies(error -> {
                    AppException appEx = (AppException) error;
                    assertThat(appEx.getErrorCode()).isEqualTo(ServicedeskErrorCode.DUPLICATE_ACTIVE_WARRANTY_POLICY);
                    assertThat(appEx.getMessage()).contains("Chính sách TOTO Sứ 10 năm");
                });

        verify(warrantyPolicyRepository, never()).saveAndFlush(any());
    }

    @Test
    void convertsConcurrentUniqueIndexViolationOnCreateIntoDuplicateActivePolicyException() {
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("TOTO").status("ACTIVE").build()));
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.of(CatalogCategorySummaryDto.builder().categoryId(categoryId).categoryName("Bồn cầu").status("ACTIVE").build()));
        when(warrantyPolicyRepository.existsByBrandIdAndCategoryIdAndStatus(brandId, categoryId, WarrantyPolicyStatus.ACTIVE)).thenReturn(false);
        when(warrantyPolicyRepository.nextPolicyCodeSequence()).thenReturn(2L);
        when(iamApi.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(warrantyPolicyRepository.saveAndFlush(any(WarrantyPolicy.class))).thenThrow(new DataIntegrityViolationException("uq_warranty_policies_active_brand_category"));
        when(warrantyPolicyRepository.search(eq(null), eq(WarrantyPolicyStatus.ACTIVE.name()), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThatThrownBy(() -> service.create(request(brandId, categoryId)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ServicedeskErrorCode.DUPLICATE_ACTIVE_WARRANTY_POLICY);
    }

    @Test
    void rejectsUnknownBrandOrCategoryWhenCreating() {
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(brandId, categoryId)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ServicedeskErrorCode.WARRANTY_POLICY_BRAND_NOT_FOUND);

        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("TOTO").status("ACTIVE").build()));
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(brandId, categoryId)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ServicedeskErrorCode.WARRANTY_POLICY_CATEGORY_NOT_FOUND);
    }

    @Test
    void updatesPolicyTrimsFieldsAndPublishesUpdatedEvent() {
        UUID policyId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WarrantyPolicy existing = WarrantyPolicy.builder()
                .warrantyPolicyId(policyId)
                .policyCode("POL-00001")
                .policyName("Cũ")
                .brandId(brandId)
                .categoryId(categoryId)
                .warrantyPeriodMonths(24)
                .coverageConditions("Điều khoản cũ")
                .status(WarrantyPolicyStatus.ACTIVE)
                .build();
        UpsertWarrantyPolicyRequest updateReq = UpsertWarrantyPolicyRequest.builder()
                .policyName("  Bảo hành mới 36 tháng  ")
                .brandId(brandId)
                .categoryId(categoryId)
                .warrantyPeriodMonths(36)
                .coverageConditions("  Điều khoản mới 36 tháng  ")
                .status(WarrantyPolicyStatus.ACTIVE)
                .build();

        when(warrantyPolicyRepository.findById(policyId)).thenReturn(Optional.of(existing));
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("TOTO").status("ACTIVE").build()));
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.of(CatalogCategorySummaryDto.builder().categoryId(categoryId).categoryName("Sen vòi").status("ACTIVE").build()));
        when(warrantyPolicyRepository.existsByBrandIdAndCategoryIdAndStatusAndWarrantyPolicyIdNot(
                brandId, categoryId, WarrantyPolicyStatus.ACTIVE, policyId)).thenReturn(false);
        doAnswer(inv -> {
            UpsertWarrantyPolicyRequest req = inv.getArgument(0);
            WarrantyPolicy target = inv.getArgument(1);
            target.setWarrantyPeriodMonths(req.warrantyPeriodMonths());
            target.setStatus(req.status());
            return null;
        }).when(warrantyPolicyMapper).update(eq(updateReq), eq(existing));
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(warrantyPolicyRepository.saveAndFlush(existing)).thenReturn(existing);
        when(warrantyPolicyMapper.toResponse(existing)).thenReturn(WarrantyPolicyResponse.builder().policyId(policyId).build());
        when(catalogApi.getVariantsByWarrantyPolicyId(policyId)).thenReturn(List.of());

        service.update(policyId, updateReq);

        assertThat(existing.getPolicyName()).isEqualTo("Bảo hành mới 36 tháng");
        assertThat(existing.getCoverageConditions()).isEqualTo("Điều khoản mới 36 tháng");
        assertThat(existing.getWarrantyPeriodMonths()).isEqualTo(36);
        assertThat(existing.getUpdatedBy()).isEqualTo(actorId);
        ArgumentCaptor<WarrantyPolicyChangedEvent> eventCaptor = ArgumentCaptor.forClass(WarrantyPolicyChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("UPDATED");
        assertThat(eventCaptor.getValue().oldData()).containsEntry("policyName", "Cũ");
        assertThat(eventCaptor.getValue().newData()).containsEntry("policyName", "Bảo hành mới 36 tháng");
    }

    @Test
    void deactivatesActivePolicyAndSkipsSaveWhenAlreadyInactive() {
        UUID policyId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WarrantyPolicy activePolicy = WarrantyPolicy.builder()
                .warrantyPolicyId(policyId)
                .policyCode("POL-00005")
                .brandId(brandId)
                .categoryId(categoryId)
                .status(WarrantyPolicyStatus.ACTIVE)
                .build();
        when(warrantyPolicyRepository.findById(policyId)).thenReturn(Optional.of(activePolicy));
        when(iamApi.getCurrentUserId()).thenReturn(actorId);
        when(warrantyPolicyRepository.saveAndFlush(activePolicy)).thenReturn(activePolicy);
        when(warrantyPolicyMapper.toResponse(activePolicy)).thenReturn(WarrantyPolicyResponse.builder().policyId(policyId).status(WarrantyPolicyStatus.INACTIVE).build());
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.empty());
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.empty());
        when(catalogApi.getVariantsByWarrantyPolicyId(policyId)).thenReturn(List.of());

        service.deactivate(policyId);
        assertThat(activePolicy.getStatus()).isEqualTo(WarrantyPolicyStatus.INACTIVE);
        verify(warrantyPolicyRepository).saveAndFlush(activePolicy);

        // Calling deactivate again when already INACTIVE is idempotent and does not write again
        service.deactivate(policyId);
        verify(warrantyPolicyRepository).saveAndFlush(activePolicy);
    }

    @Test
    void blocksDeleteWhenAVariantReferencesThePolicy() {
        UUID policyId = UUID.randomUUID();
        WarrantyPolicy policy = WarrantyPolicy.builder().warrantyPolicyId(policyId).policyCode("POL-00001").build();
        when(warrantyPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(catalogApi.getVariantsByWarrantyPolicyId(policyId)).thenReturn(List.of(
                CatalogVariantWarrantyReferenceDto.builder().variantId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.delete(policyId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ServicedeskErrorCode.WARRANTY_POLICY_IN_USE);

        verify(warrantyPolicyRepository, never()).delete(any());
    }

    @Test
    void deletesUnreferencedPolicyOrConvertsDatabaseFkViolationToInUse() {
        UUID policyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WarrantyPolicy policy = WarrantyPolicy.builder().warrantyPolicyId(policyId).policyCode("POL-00008").status(WarrantyPolicyStatus.INACTIVE).build();
        when(warrantyPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(catalogApi.getVariantsByWarrantyPolicyId(policyId)).thenReturn(List.of());
        when(iamApi.getCurrentUserId()).thenReturn(actorId);

        service.delete(policyId);

        verify(warrantyPolicyRepository).delete(policy);
        verify(warrantyPolicyRepository).flush();
        ArgumentCaptor<WarrantyPolicyChangedEvent> eventCaptor = ArgumentCaptor.forClass(WarrantyPolicyChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("DELETED");

        doThrow(new DataIntegrityViolationException("fk_warranty_records_policy")).when(warrantyPolicyRepository).flush();
        assertThatThrownBy(() -> service.delete(policyId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ServicedeskErrorCode.WARRANTY_POLICY_IN_USE);
    }

    @Test
    void detailReferenceDataAndSearchReturnExpectedProjections() {
        UUID policyId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        WarrantyPolicy policy = WarrantyPolicy.builder()
                .warrantyPolicyId(policyId)
                .policyCode("POL-00009")
                .brandId(brandId)
                .categoryId(categoryId)
                .status(WarrantyPolicyStatus.ACTIVE)
                .build();
        when(warrantyPolicyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(warrantyPolicyMapper.toResponse(policy)).thenReturn(WarrantyPolicyResponse.builder().policyId(policyId).policyCode("POL-00009").build());
        when(catalogApi.getBrandSummary(brandId)).thenReturn(Optional.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("INAX").status("ACTIVE").build()));
        when(catalogApi.getCategorySummary(categoryId)).thenReturn(Optional.of(CatalogCategorySummaryDto.builder().categoryId(categoryId).categoryName("Chậu rửa").status("ACTIVE").build()));
        when(catalogApi.getVariantsByWarrantyPolicyId(policyId)).thenReturn(List.of(
                CatalogVariantWarrantyReferenceDto.builder().variantId(UUID.randomUUID()).sku("INAX-01").variantName("Chậu dương bàn").build()));

        WarrantyPolicyDetailResponse detail = service.detail(policyId);
        assertThat(detail.getPolicy().getBrandName()).isEqualTo("INAX");
        assertThat(detail.getPolicy().getVariantReferenceCount()).isEqualTo(1L);
        assertThat(detail.getVariants()).singleElement().satisfies(v -> assertThat(v.sku()).isEqualTo("INAX-01"));

        when(warrantyPolicyRepository.search("inax", "ACTIVE", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(policy), PageRequest.of(0, 20), 1));
        var page = service.search("  inax  ", WarrantyPolicyStatus.ACTIVE, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1L);

        when(catalogApi.getActiveBrands()).thenReturn(List.of(CatalogBrandSummaryDto.builder().brandId(brandId).brandName("INAX").status("ACTIVE").build()));
        when(catalogApi.getActiveCategories()).thenReturn(List.of(CatalogCategorySummaryDto.builder().categoryId(categoryId).categoryName("Chậu rửa").status("ACTIVE").build()));
        WarrantyPolicyReferenceDataResponse refData = service.referenceData();
        assertThat(refData.brands()).hasSize(1);
        assertThat(refData.categories()).hasSize(1);

        UUID missingId = UUID.randomUUID();
        when(warrantyPolicyRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(missingId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ServicedeskErrorCode.WARRANTY_POLICY_NOT_FOUND);
    }

    private UpsertWarrantyPolicyRequest request(UUID brandId, UUID categoryId) {
        return UpsertWarrantyPolicyRequest.builder().policyName("Bảo hành sứ vệ sinh TOTO")
                .brandId(brandId).categoryId(categoryId).warrantyPeriodMonths(120)
                .coverageConditions("Bảo hành 10 năm phần sứ.").status(WarrantyPolicyStatus.ACTIVE).build();
    }
}
