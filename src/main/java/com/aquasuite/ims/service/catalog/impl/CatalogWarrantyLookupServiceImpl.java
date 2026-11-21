package com.aquasuite.ims.service.catalog.impl;

import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.entity.catalog.WarrantyPolicy;
import com.aquasuite.ims.repository.catalog.WarrantyPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogWarrantyLookupServiceImpl implements CatalogWarrantyLookupService {

    private final com.aquasuite.ims.repository.catalog.ProductVariantRepository productVariantRepository;
    private final com.aquasuite.ims.repository.catalog.ProductRepository productRepository;
    private final WarrantyPolicyRepository warrantyPolicyRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto> getWarrantyInfoByVariantId(UUID variantId) {
        if (variantId == null) {
            return Optional.empty();
        }
        return productVariantRepository.findById(variantId).map(variant -> {
            String productName = variant.getProductId() == null
                    ? null
                    : productRepository.findById(variant.getProductId())
                            .map(com.aquasuite.ims.entity.catalog.Product::getProductName).orElse(null);

            WarrantyPolicy policy = variant.getWarrantyPolicyId() == null
                    ? productRepository.findById(variant.getProductId())
                            .filter(product -> product.getBrandId() != null)
                            .flatMap(product -> warrantyPolicyRepository.findFirstByBrandIdAndCategoryIdAndStatus(
                                    product.getBrandId(), product.getCategoryId(), "ACTIVE"))
                            .orElse(null)
                    : warrantyPolicyRepository.findById(variant.getWarrantyPolicyId()).orElse(null);

            return com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto.builder()
                    .variantId(variant.getVariantId())
                    .sku(variant.getSku())
                    .variantName(variant.getVariantName())
                    .productName(productName)
                    .serialTracking(Boolean.TRUE.equals(variant.getSerialTracking()))
                    .warrantyPolicyId(policy != null ? policy.getWarrantyPolicyId() : null)
                    .policyCode(policy != null ? policy.getPolicyCode() : null)
                    .policyName(policy != null ? policy.getPolicyName() : null)
                    .warrantyMonths(policy != null ? policy.getWarrantyMonths() : 0)
                    .warrantyTerms(policy != null ? policy.getWarrantyTerms() : null)
                    .policyStatus(policy != null ? policy.getStatus() : null)
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto> getWarrantyPolicyById(UUID policyId) {
        if (policyId == null) {
            return Optional.empty();
        }
        return warrantyPolicyRepository.findById(policyId).map(this::toPolicyDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto> getActiveWarrantyPolicies() {
        return warrantyPolicyRepository.findByStatusOrderByWarrantyMonthsAscPolicyNameAsc("ACTIVE")
                .stream()
                .map(this::toPolicyDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogWarrantyPolicyDto> getActiveWarrantyPolicyByBrandAndCategory(UUID brandId, UUID categoryId) {
        if (brandId == null || categoryId == null) {
            return Optional.empty();
        }
        return warrantyPolicyRepository
                .findFirstByBrandIdAndCategoryIdAndStatus(brandId, categoryId, "ACTIVE")
                .map(this::toPolicyDto);
    }

    private com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto toPolicyDto(WarrantyPolicy policy) {
        return com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto.builder()
                .warrantyPolicyId(policy.getWarrantyPolicyId())
                .policyCode(policy.getPolicyCode())
                .policyName(policy.getPolicyName())
                .warrantyMonths(policy.getWarrantyMonths())
                .warrantyTerms(policy.getWarrantyTerms())
                .policyStatus(policy.getStatus())
                .brandId(policy.getBrandId())
                .categoryId(policy.getCategoryId())
                .build();
    }
}

