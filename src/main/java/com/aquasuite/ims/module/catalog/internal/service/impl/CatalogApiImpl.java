package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogApiImpl implements CatalogApi {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final BundleQueryService bundleQueryService;

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogSkuSummaryDto> getSkuSummary(UUID variantId) {
        if (variantId == null) return Optional.empty();
        return productVariantRepository.findById(variantId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogSkuSummaryDto> getSkuSummaryBySku(String sku) {
        if (sku == null || sku.isBlank()) return Optional.empty();
        return productVariantRepository.findBySku(sku.trim()).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CatalogSkuSummaryDto> getSkuSummaries(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return Collections.emptyMap();
        return productVariantRepository.findAllById(variantIds).stream()
                .map(this::toDto)
                .collect(Collectors.toMap(CatalogSkuSummaryDto::variantId, dto -> dto));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogBundleSummaryDto> getBundleSummary(UUID bundleId) {
        if (bundleId == null) return Optional.empty();
        try {
            BundleResponse bundle = bundleQueryService.getBundleDetails(bundleId);
            if (bundle == null) return Optional.empty();

            List<CatalogBundleSummaryDto.CatalogBundleItemDto> items = bundle.items() == null ? List.of() :
                    bundle.items().stream()
                            .map(item -> CatalogBundleSummaryDto.CatalogBundleItemDto.builder()
                                    .variantId(item.variantId())
                                    .sku(item.sku())
                                    .variantName(item.name())
                                    .quantity(item.quantity())
                                    .sellingPrice(item.sellingPrice())
                                    .build())
                            .toList();

            return Optional.of(CatalogBundleSummaryDto.builder()
                    .bundleId(bundle.bundleId())
                    .bundleCode(bundle.productCode())
                    .bundleName(bundle.name())
                    .packagePrice(bundle.packagePrice())
                    .status(bundle.productStatus())
                    .items(items)
                    .build());
        } catch (Exception ex) {
            log.warn("Bundle {} not found or error retrieving bundle: {}", bundleId, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogSkuSummaryDto> getSkusByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) return Collections.emptyList();
        return productVariantRepository.findByProductIdIn(productIds).stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getVariantIdsByProductId(UUID productId) {
        if (productId == null) return Collections.emptyList();
        return productVariantRepository.findByProductId(productId).stream()
                .map(ProductVariant::getVariantId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogSkuSummaryDto> getAllActiveSkus() {
        return productVariantRepository.findByStatus(VariantStatus.ACTIVE).stream()
                .map(this::toDto)
                .toList();
    }

    private CatalogSkuSummaryDto toDto(ProductVariant variant) {
        String productName = null;
        if (variant.getProductId() != null) {
            productName = productRepository.findById(variant.getProductId())
                    .map(Product::getProductName)
                    .orElse(null);
        }

        return CatalogSkuSummaryDto.builder()
                .variantId(variant.getVariantId())
                .productId(variant.getProductId())
                .sku(variant.getSku())
                .variantName(variant.getVariantName())
                .productName(productName)
                .unit(variant.getUnit())
                .sellingPrice(variant.getSellingPrice())
                .costPrice(variant.getCostPrice())
                .serialTracking(variant.getSerialTracking())
                .status(variant.getStatus() != null ? variant.getStatus().name() : null)
                .build();
    }
}
