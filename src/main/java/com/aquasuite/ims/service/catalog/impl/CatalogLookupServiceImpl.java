package com.aquasuite.ims.service.catalog.impl;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogBrandSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogCategorySummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogVariantWarrantyReferenceDto;
import com.aquasuite.ims.entity.catalog.Brand;
import com.aquasuite.ims.entity.catalog.Category;
import com.aquasuite.ims.entity.catalog.Product;
import com.aquasuite.ims.entity.catalog.ProductImage;
import com.aquasuite.ims.entity.catalog.ProductVariant;
import com.aquasuite.ims.enums.catalog.VariantStatus;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.repository.catalog.ProductImageRepository;
import com.aquasuite.ims.repository.catalog.ProductRepository;
import com.aquasuite.ims.repository.catalog.ProductVariantRepository;
import com.aquasuite.ims.repository.catalog.BrandRepository;
import com.aquasuite.ims.repository.catalog.CategoryRepository;
import com.aquasuite.ims.service.catalog.BundleQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogLookupServiceImpl implements CatalogLookupService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogBrandSummaryDto> getBrandSummary(UUID brandId) {
        if (brandId == null) return Optional.empty();
        return brandRepository.findById(brandId).map(this::toBrandDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogCategorySummaryDto> getCategorySummary(UUID categoryId) {
        if (categoryId == null) return Optional.empty();
        return categoryRepository.findById(categoryId).map(this::toCategoryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogBrandSummaryDto> getActiveBrands() {
        return brandRepository.findAllActive().stream().map(this::toBrandDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogCategorySummaryDto> getActiveCategories() {
        return categoryRepository.findAllActive().stream().map(this::toCategoryDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogVariantWarrantyReferenceDto> getVariantsByWarrantyPolicyId(UUID warrantyPolicyId) {
        if (warrantyPolicyId == null) return Collections.emptyList();
        return productVariantRepository.findByWarrantyPolicyId(warrantyPolicyId).stream()
                .map(variant -> CatalogVariantWarrantyReferenceDto.builder()
                        .variantId(variant.getVariantId()).sku(variant.getSku())
                        .variantName(variant.getVariantName()).build())
                .toList();
    }

    private CatalogBrandSummaryDto toBrandDto(Brand brand) {
        return CatalogBrandSummaryDto.builder().brandId(brand.getBrandId()).brandCode(brand.getBrandCode())
                .brandName(brand.getBrandName()).status(brand.getStatus().name()).build();
    }

    private CatalogCategorySummaryDto toCategoryDto(Category category) {
        return CatalogCategorySummaryDto.builder().categoryId(category.getCategoryId())
                .categoryCode(category.getCategoryCode()).categoryName(category.getCategoryName())
                .status(category.getStatus().name()).build();
    }

    private CatalogSkuSummaryDto toDto(ProductVariant variant) {
        String productName = null;
        String productStatus = null;
        if (variant.getProductId() != null) {
            Optional<Product> productOpt = productRepository.findById(variant.getProductId());
            if (productOpt.isPresent()) {
                Product product = productOpt.get();
                productName = product.getProductName();
                productStatus = product.getStatus() != null ? product.getStatus().name() : null;
            }
        }

        String imageUrl = resolvePrimaryImageUrl(variant);

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
                .productStatus(productStatus)
                .imageUrl(imageUrl)
                .build();
    }

    private String resolvePrimaryImageUrl(ProductVariant variant) {
        if (variant.getVariantId() != null) {
            Optional<String> variantImage = pickPrimaryImageUrl(
                    productImageRepository.findByVariantId(variant.getVariantId()));
            if (variantImage.isPresent()) {
                return variantImage.get();
            }
        }
        if (variant.getProductId() != null) {
            return pickPrimaryImageUrl(productImageRepository.findByProductId(variant.getProductId()))
                    .orElse(null);
        }
        return null;
    }

    private Optional<String> pickPrimaryImageUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return Optional.empty();
        }
        return images.stream()
                .sorted(Comparator
                        .comparing(ProductImage::getIsPrimary, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductImage::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ProductImage::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst();
    }
}
