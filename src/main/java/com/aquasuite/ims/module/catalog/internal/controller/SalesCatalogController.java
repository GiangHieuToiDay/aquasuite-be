package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductImage;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundlePageRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.payload.res.SalesCatalogItemResponse;
import com.aquasuite.ims.module.catalog.internal.repository.BrandRepository;
import com.aquasuite.ims.module.catalog.internal.repository.CategoryRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductImageRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller phục vụ danh mục sản phẩm và combo thiết bị cho chức năng lập Báo giá (Sales Quotation).
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/sales/catalog-items", "/api/v1/catalog/sales-items"})
@RequiredArgsConstructor
public class SalesCatalogController {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductImageRepository imageRepository;
    private final InventoryApi inventoryApi;
    private final BundleQueryService bundleQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SalesCatalogItemResponse>>> getSalesCatalogItems() {
        List<ProductVariant> variants = variantRepository.findAll().stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .toList();

        Set<UUID> productIds = variants.stream().map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        Set<UUID> categoryIds = productMap.values().stream().map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> categoryNameMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryName));

        Set<UUID> brandIds = productMap.values().stream().map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> brandNameMap = brandRepository.findAllById(brandIds).stream()
                .collect(Collectors.toMap(Brand::getBrandId, Brand::getBrandName));

        Set<UUID> variantIds = variants.stream().map(ProductVariant::getVariantId).collect(Collectors.toSet());
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);

        List<ProductImage> allImages = imageRepository.findAll();
        Map<UUID, String> imageMap = new HashMap<>();
        for (ProductImage img : allImages) {
            if (img.getVariantId() != null && !imageMap.containsKey(img.getVariantId())) {
                imageMap.put(img.getVariantId(), img.getImageUrl());
            } else if (img.getProductId() != null && !imageMap.containsKey(img.getProductId())) {
                imageMap.put(img.getProductId(), img.getImageUrl());
            }
        }

        List<SalesCatalogItemResponse> result = variants.stream()
                .filter(v -> {
                    Product product = productMap.get(v.getProductId());
                    return product == null || product.getProductType() != ProductType.BUNDLE;
                }).map(v -> {
            Product p = productMap.get(v.getProductId());
            boolean isCombo = p != null && p.getProductType() == ProductType.BUNDLE;
            String category = (p != null && p.getCategoryId() != null) ? categoryNameMap.getOrDefault(p.getCategoryId(), "Thiết bị") : "Thiết bị";
            String brand = (p != null && p.getBrandId() != null) ? brandNameMap.getOrDefault(p.getBrandId(), "TOTO") : "TOTO";
            BigDecimal stock = stockMap.containsKey(v.getVariantId()) ? stockMap.get(v.getVariantId()).availableQty() : BigDecimal.valueOf(50);
            String imgUrl = imageMap.getOrDefault(v.getVariantId(), imageMap.get(v.getProductId()));

            return SalesCatalogItemResponse.builder()
                    .variantId(v.getVariantId())
                    .sku(v.getSku())
                    .name(v.getVariantName())
                    .brand(brand)
                    .category(isCombo ? "Combo Dự Án" : category)
                    .unit(v.getUnit())
                    .sellingPrice(v.getSellingPrice())
                    .availableStock(stock)
                    .isCombo(isCombo)
                    .description(p != null ? p.getDescription() : "")
                    .imageUrl(imgUrl)
                    .specifications(p != null ? p.getSpecifications() : null)
                    .build();
        }).collect(Collectors.toCollection(ArrayList::new));

        // Combo IDs belong to Product, and newly created combos have no ProductVariant.
        int page = 0;
        com.aquasuite.ims.share.dto.PageResponse<BundleResponse> bundles;
        do {
            bundles = bundleQueryService.getBundleList(
                    new BundlePageRequest(page++, 100, null, ProductStatus.ACTIVE, null));
            Set<UUID> componentIds = bundles.getItems().stream()
                    .flatMap(bundle -> bundle.items().stream())
                    .map(BundleResponse.Item::variantId).collect(Collectors.toSet());
            Map<UUID, StockSummaryDto> componentStock = inventoryApi.getStockSummaries(componentIds);
            for (BundleResponse bundle : bundles.getItems()) {
                Map<UUID, Integer> quantities = new HashMap<>();
                bundle.items().forEach(item -> quantities.merge(item.variantId(), item.quantity(), Integer::sum));
                BigDecimal stock = quantities.entrySet().stream().map(entry -> {
                    StockSummaryDto summary = componentStock.get(entry.getKey());
                    return summary == null || entry.getValue() <= 0 ? BigDecimal.ZERO
                            : summary.availableQty().max(BigDecimal.ZERO)
                                    .divideToIntegralValue(BigDecimal.valueOf(entry.getValue()));
                }).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                result.add(SalesCatalogItemResponse.builder()
                        // Legacy field: for isCombo=true, send this ID as combos[].comboId.
                        .variantId(bundle.bundleId())
                        .sku(bundle.productCode())
                        .name(bundle.name())
                        .category("Combo Dự Án")
                        .unit("Bộ")
                        .sellingPrice(bundle.packagePrice())
                        .availableStock(stock)
                        .isCombo(true)
                        .description(bundle.description())
                        .imageUrl(imageMap.get(bundle.bundleId()))
                        .components(bundle.items().stream()
                                .map(item -> item.name() + " x" + item.quantity()).toList())
                        .build());
            }
        } while (page < bundles.getTotalPages());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}

