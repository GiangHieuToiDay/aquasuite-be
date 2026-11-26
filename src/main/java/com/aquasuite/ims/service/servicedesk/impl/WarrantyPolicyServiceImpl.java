package com.aquasuite.ims.service.servicedesk.impl;

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
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyVariantResponse;
import com.aquasuite.ims.repository.servicedesk.WarrantyPolicyRepository;
import com.aquasuite.ims.service.servicedesk.WarrantyPolicyService;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static lombok.AccessLevel.PRIVATE;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class WarrantyPolicyServiceImpl implements WarrantyPolicyService {

    WarrantyPolicyRepository warrantyPolicyRepository;
    WarrantyPolicyMapper warrantyPolicyMapper;
    CatalogLookupService catalogApi;
    IamQueryService iamApi;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarrantyPolicyResponse> search(String keyword, WarrantyPolicyStatus status, Pageable pageable) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Pageable safePageable = com.aquasuite.ims.util.PageableUtils.sanitizePageable(
                pageable,
                java.util.Map.ofEntries(
                        java.util.Map.entry("created_at", "created_at"),
                        java.util.Map.entry("createdAt", "created_at"),
                        java.util.Map.entry("updated_at", "updated_at"),
                        java.util.Map.entry("updatedAt", "updated_at"),
                        java.util.Map.entry("policy_code", "policy_code"),
                        java.util.Map.entry("policyCode", "policy_code"),
                        java.util.Map.entry("policy_name", "policy_name"),
                        java.util.Map.entry("policyName", "policy_name"),
                        java.util.Map.entry("warranty_period_months", "warranty_period_months"),
                        java.util.Map.entry("warrantyPeriodMonths", "warranty_period_months"),
                        java.util.Map.entry("status", "status")
                ));
        return PageResponse.from(warrantyPolicyRepository.search(normalizedKeyword,
                status == null ? null : status.name(), safePageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyPolicyDetailResponse detail(UUID policyId) {
        WarrantyPolicy policy = findPolicy(policyId);
        List<WarrantyPolicyVariantResponse> variants = catalogApi.getVariantsByWarrantyPolicyId(policyId).stream()
                .map(this::toVariantResponse).toList();
        return WarrantyPolicyDetailResponse.builder().policy(toResponse(policy)).variants(variants).build();
    }

    @Override
    @Transactional
    public WarrantyPolicyResponse create(UpsertWarrantyPolicyRequest request) {
        validateCatalogReferences(request);
        assertActivePolicyUnique(request.brandId(), request.categoryId(), request.status(), null);

        UUID actorId = iamApi.getCurrentUserId();
        WarrantyPolicy policy = WarrantyPolicy.builder()
                .policyCode(nextPolicyCode())
                .policyName(request.policyName().trim())
                .brandId(request.brandId())
                .categoryId(request.categoryId())
                .warrantyPeriodMonths(request.warrantyPeriodMonths())
                .coverageConditions(request.coverageConditions().trim())
                .status(request.status())
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        try {
            policy = warrantyPolicyRepository.saveAndFlush(policy);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateActivePolicy(request.brandId(), request.categoryId());
        }
        publish(policy, actorId, "CREATED", null, snapshot(policy));
        return toResponse(policy);
    }

    @Override
    @Transactional
    public WarrantyPolicyResponse update(UUID policyId, UpsertWarrantyPolicyRequest request) {
        WarrantyPolicy policy = findPolicy(policyId);
        Map<String, Object> oldData = snapshot(policy);
        validateCatalogReferences(request);
        assertActivePolicyUnique(request.brandId(), request.categoryId(), request.status(), policyId);

        warrantyPolicyMapper.update(request, policy);
        policy.setPolicyName(request.policyName().trim());
        policy.setCoverageConditions(request.coverageConditions().trim());
        UUID actorId = iamApi.getCurrentUserId();
        policy.setUpdatedBy(actorId);
        try {
            policy = warrantyPolicyRepository.saveAndFlush(policy);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateActivePolicy(request.brandId(), request.categoryId());
        }
        publish(policy, actorId, "UPDATED", oldData, snapshot(policy));
        return toResponse(policy);
    }

    @Override
    @Transactional
    public WarrantyPolicyResponse deactivate(UUID policyId) {
        WarrantyPolicy policy = findPolicy(policyId);
        if (policy.getStatus() == WarrantyPolicyStatus.INACTIVE) return toResponse(policy);
        Map<String, Object> oldData = snapshot(policy);
        UUID actorId = iamApi.getCurrentUserId();
        policy.setStatus(WarrantyPolicyStatus.INACTIVE);
        policy.setUpdatedBy(actorId);
        policy = warrantyPolicyRepository.saveAndFlush(policy);
        publish(policy, actorId, "DEACTIVATED", oldData, snapshot(policy));
        return toResponse(policy);
    }

    @Override
    @Transactional
    public void delete(UUID policyId) {
        WarrantyPolicy policy = findPolicy(policyId);
        long referenceCount = catalogApi.getVariantsByWarrantyPolicyId(policyId).size();
        if (referenceCount > 0) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_POLICY_IN_USE, referenceCount);
        }
        UUID actorId = iamApi.getCurrentUserId();
        Map<String, Object> oldData = snapshot(policy);
        try {
            warrantyPolicyRepository.delete(policy);
            warrantyPolicyRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_POLICY_IN_USE, 1);
        }
        publish(policy, actorId, "DELETED", oldData, null);
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyPolicyReferenceDataResponse referenceData() {
        return WarrantyPolicyReferenceDataResponse.builder()
                .brands(catalogApi.getActiveBrands())
                .categories(catalogApi.getActiveCategories())
                .build();
    }

    private WarrantyPolicy findPolicy(UUID policyId) {
        return warrantyPolicyRepository.findById(policyId)
                .orElseThrow(() -> new AppException(ServicedeskErrorCode.WARRANTY_POLICY_NOT_FOUND, policyId));
    }

    private void validateCatalogReferences(UpsertWarrantyPolicyRequest request) {
        if (catalogApi.getBrandSummary(request.brandId()).isEmpty()) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_POLICY_BRAND_NOT_FOUND, request.brandId());
        }
        if (catalogApi.getCategorySummary(request.categoryId()).isEmpty()) {
            throw new AppException(ServicedeskErrorCode.WARRANTY_POLICY_CATEGORY_NOT_FOUND, request.categoryId());
        }
    }

    private void assertActivePolicyUnique(UUID brandId, UUID categoryId, WarrantyPolicyStatus status, UUID excludedPolicyId) {
        if (status != WarrantyPolicyStatus.ACTIVE) return;
        boolean exists = excludedPolicyId == null
                ? warrantyPolicyRepository.existsByBrandIdAndCategoryIdAndStatus(brandId, categoryId, WarrantyPolicyStatus.ACTIVE)
                : warrantyPolicyRepository.existsByBrandIdAndCategoryIdAndStatusAndWarrantyPolicyIdNot(
                        brandId, categoryId, WarrantyPolicyStatus.ACTIVE, excludedPolicyId);
        if (exists) throw duplicateActivePolicy(brandId, categoryId);
    }

    private AppException duplicateActivePolicy(UUID brandId, UUID categoryId) {
        String existingName = warrantyPolicyRepository.search(null, WarrantyPolicyStatus.ACTIVE.name(),
                        org.springframework.data.domain.PageRequest.of(0, 1000)).stream()
                .filter(policy -> policy.getBrandId().equals(brandId) && policy.getCategoryId().equals(categoryId))
                .map(WarrantyPolicy::getPolicyName).findFirst().orElse("chính sách hiện có");
        return new AppException(ServicedeskErrorCode.DUPLICATE_ACTIVE_WARRANTY_POLICY, existingName);
    }

    private String nextPolicyCode() {
        return "POL-%05d".formatted(warrantyPolicyRepository.nextPolicyCodeSequence());
    }

    private WarrantyPolicyResponse toResponse(WarrantyPolicy policy) {
        WarrantyPolicyResponse response = warrantyPolicyMapper.toResponse(policy);
        CatalogBrandSummaryDto brand = catalogApi.getBrandSummary(policy.getBrandId()).orElse(null);
        CatalogCategorySummaryDto category = catalogApi.getCategorySummary(policy.getCategoryId()).orElse(null);
        response.setBrandName(brand == null ? null : brand.brandName());
        response.setCategoryName(category == null ? null : category.categoryName());
        response.setVariantReferenceCount((long) catalogApi.getVariantsByWarrantyPolicyId(policy.getWarrantyPolicyId()).size());
        return response;
    }

    private WarrantyPolicyVariantResponse toVariantResponse(CatalogVariantWarrantyReferenceDto variant) {
        return WarrantyPolicyVariantResponse.builder().variantId(variant.variantId()).sku(variant.sku())
                .variantName(variant.variantName()).build();
    }

    private Map<String, Object> snapshot(WarrantyPolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("policyCode", policy.getPolicyCode());
        data.put("policyName", policy.getPolicyName());
        data.put("brandId", policy.getBrandId());
        data.put("categoryId", policy.getCategoryId());
        data.put("warrantyPeriodMonths", policy.getWarrantyPeriodMonths());
        data.put("coverageConditions", policy.getCoverageConditions());
        data.put("status", policy.getStatus() == null ? null : policy.getStatus().name());
        return data;
    }

    private void publish(WarrantyPolicy policy, UUID actorId, String action,
                         Map<String, Object> oldData, Map<String, Object> newData) {
        eventPublisher.publishEvent(new WarrantyPolicyChangedEvent(UUID.randomUUID(), actorId, policy.getWarrantyPolicyId(),
                action, oldData, newData, Instant.now()));
    }
}
