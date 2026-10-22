package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogApiImpl implements CatalogApi {

    private final ProductVariantRepository productVariantRepository;

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

    private CatalogSkuSummaryDto toDto(ProductVariant variant) {
        return CatalogSkuSummaryDto.builder()
                .variantId(variant.getVariantId())
                .productId(variant.getProductId())
                .sku(variant.getSku())
                .variantName(variant.getVariantName())
                .unit(variant.getUnit())
                .sellingPrice(variant.getSellingPrice())
                .costPrice(variant.getCostPrice())
                .serialTracking(variant.getSerialTracking())
                .status(variant.getStatus() != null ? variant.getStatus().name() : null)
                .build();
    }
}
