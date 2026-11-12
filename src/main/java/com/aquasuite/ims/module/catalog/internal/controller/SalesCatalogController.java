package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.entity.Brand;
import com.aquasuite.ims.module.catalog.internal.entity.Category;
import com.aquasuite.ims.module.catalog.internal.entity.Product;
import com.aquasuite.ims.module.catalog.internal.entity.ProductImage;
import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundlePageRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.SalesCatalogItemResponse;
import com.aquasuite.ims.module.catalog.internal.repository.BrandRepository;
import com.aquasuite.ims.module.catalog.internal.repository.CategoryRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductImageRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
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
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller phục vụ danh mục sản phẩm và combo thiết bị cho chức năng lập Báo giá (Sales Quotation) và POS.
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

        List<Category> allCategories = categoryRepository.findAll();
        Map<UUID, String> categoryNameMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryName));

        List<Brand> allBrands = brandRepository.findAll();
        Map<UUID, String> brandNameMap = allBrands.stream()
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

        List<SalesCatalogItemResponse> result = new ArrayList<>();

        for (ProductVariant v : variants) {
            Product p = productMap.get(v.getProductId());
            if (p != null && p.getProductType() == ProductType.BUNDLE) {
                continue;
            }
            boolean isCombo = false;
            String category = (p != null && p.getCategoryId() != null) ? categoryNameMap.getOrDefault(p.getCategoryId(), "Thiết bị") : "Thiết bị";
            String brand = (p != null && p.getBrandId() != null) ? brandNameMap.getOrDefault(p.getBrandId(), "TOTO") : "TOTO";
            BigDecimal stock = stockMap.containsKey(v.getVariantId()) ? stockMap.get(v.getVariantId()).availableQty() : BigDecimal.valueOf(50);
            String imgUrl = imageMap.getOrDefault(v.getVariantId(), imageMap.get(v.getProductId()));

            result.add(SalesCatalogItemResponse.builder()
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
                    .build());
        }

        // Truy vấn các gói Combo (Bundle) đã phát hành từ Database
        try {
            int page = 0;
            com.aquasuite.ims.share.dto.PageResponse<BundleResponse> bundlePage;
            do {
                bundlePage = bundleQueryService.getBundleList(new BundlePageRequest(page++, 100, null, ProductStatus.ACTIVE, null));
                if (bundlePage == null || bundlePage.getItems() == null) {
                    break;
                }
                for (BundleResponse b : bundlePage.getItems()) {
                    String bundleBrand = b.brandId() != null ? brandNameMap.getOrDefault(b.brandId(), "TOTO") : "TOTO";
                    String imgUrl = imageMap.get(b.bundleId());

                    BigDecimal minComboStock = BigDecimal.ZERO;
                    List<String> components = new ArrayList<>();
                    if (b.items() != null && !b.items().isEmpty()) {
                        BigDecimal minStock = null;
                        for (BundleResponse.Item it : b.items()) {
                            components.add(it.name() + " (" + it.sku() + ") x" + it.quantity());
                            StockSummaryDto compStock = stockMap.get(it.variantId());
                            if (compStock == null) {
                                compStock = inventoryApi.getStockSummary(it.variantId()).orElse(null);
                            }
                            BigDecimal available = (compStock != null && compStock.availableQty() != null)
                                    ? compStock.availableQty()
                                    : BigDecimal.valueOf(50);
                            BigDecimal maxCombos = available.divide(BigDecimal.valueOf(Math.max(1, it.quantity())), 0, RoundingMode.FLOOR);
                            if (minStock == null || maxCombos.compareTo(minStock) < 0) {
                                minStock = maxCombos;
                            }
                        }
                        if (minStock != null) {
                            minComboStock = minStock.max(BigDecimal.ZERO);
                        }
                    }

                    result.add(SalesCatalogItemResponse.builder()
                            .variantId(b.bundleId())
                            .sku(b.productCode())
                            .name(b.name())
                            .brand(bundleBrand)
                            .category("Combo Dự Án")
                            .unit("Gói")
                            .sellingPrice(b.packagePrice())
                            .availableStock(minComboStock)
                            .isCombo(true)
                            .description(b.description())
                            .imageUrl(imgUrl)
                            .components(components)
                            .build());
                }
            } while (page < bundlePage.getTotalPages());
        } catch (Exception ex) {
            log.warn("Could not load published bundles into sales catalog: {}", ex.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}

