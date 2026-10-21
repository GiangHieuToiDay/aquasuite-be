package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.exception.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.ProductSearchCriteria;
import com.aquasuite.ims.module.catalog.internal.payload.res.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.ProductStorefrontService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStorefrontServiceImpl implements ProductStorefrontService {

    public static final String MSG43_NO_PRODUCTS_FOUND =
            "No products found matching your criteria. Try adjusting your filters or search keywords.";

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final InventoryApi inventoryApi;
    private final IamApi iamApi;

    @Override
    @Transactional(readOnly = true)
    public StorefrontSearchResultResponse searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        log.info("Storefront product search request: keyword='{}', categoryId={}, brandId={}, minPrice={}, maxPrice={}, inStockOnly={}",
                criteria != null ? criteria.keyword() : null,
                criteria != null ? criteria.categoryId() : null,
                criteria != null ? criteria.brandId() : null,
                criteria != null ? criteria.minPrice() : null,
                criteria != null ? criteria.maxPrice() : null,
                criteria != null ? criteria.inStockOnly() : null);

        // 1. Phân tích các bộ lọc phụ thuộc Biến thể (Price range, Color, Size, InStockOnly, Variant Keyword)
        Set<UUID> variantMatchedProductIds = null;
        Set<UUID> constrainedProductIds = null;

        if (criteria != null) {
            // A. Khớp từ khóa tại cấp độ Biến thể (SKU hoặc variantName)
            if (criteria.keyword() != null && !criteria.keyword().trim().isEmpty()) {
                variantMatchedProductIds = variantRepository.findProductIdsByVariantKeyword(criteria.keyword().trim());
            }

            // B. Bộ lọc khoảng giá (Price Range Slider)
            if (criteria.minPrice() != null || criteria.maxPrice() != null) {
                BigDecimal minP = criteria.minPrice() != null ? criteria.minPrice() : BigDecimal.ZERO;
                BigDecimal maxP = criteria.maxPrice() != null ? criteria.maxPrice() : new BigDecimal("999999999999");
                Set<UUID> priceIds = variantRepository.findProductIdsByPriceRange(minP, maxP);
                constrainedProductIds = intersect(constrainedProductIds, priceIds);
            }

            // C. Bộ lọc Màu sắc
            if (criteria.color() != null && !criteria.color().trim().isEmpty()) {
                Set<UUID> colorIds = variantRepository.findProductIdsByColor(criteria.color().trim());
                constrainedProductIds = intersect(constrainedProductIds, colorIds);
            }

            // D. Bộ lọc Kích thước / Tâm xả (Rough-in)
            if (criteria.size() != null && !criteria.size().trim().isEmpty()) {
                Set<UUID> sizeIds = variantRepository.findProductIdsBySize(criteria.size().trim());
                constrainedProductIds = intersect(constrainedProductIds, sizeIds);
            }

            // E. Bộ lọc Tồn kho ("In Stock Only" toggle)
            if (Boolean.TRUE.equals(criteria.inStockOnly())) {
                Set<UUID> inStockProductIds = findInStockProductIds();
                constrainedProductIds = intersect(constrainedProductIds, inStockProductIds);
            }
        }

        // 2. Thực hiện truy vấn Product theo Specification
        UUID catId = criteria != null ? criteria.categoryId() : null;
        UUID brId = criteria != null ? criteria.brandId() : null;
        String kw = criteria != null ? criteria.keyword() : null;

        Specification<Product> spec = ProductStorefrontSpecification.filter(
                kw, catId, brId, variantMatchedProductIds, constrainedProductIds
        );

        Page<Product> productPage = productRepository.findAll(spec, pageable);

        // 3. Xử lý kịch bản Alternative Flow (Step 2 - No Products Found): MSG43 + Popular Categories
        if (productPage.isEmpty()) {
            List<CategoryBriefResponse> popularCategories = getPopularCategories();
            return StorefrontSearchResultResponse.builder()
                    .products(new PageImpl<>(Collections.emptyList(), pageable, 0))
                    .message(MSG43_NO_PRODUCTS_FOUND)
                    .popularCategories(popularCategories)
                    .build();
        }

        List<Product> products = productPage.getContent();
        List<UUID> productIds = products.stream().map(Product::getProductId).toList();

        // 4. Batch load dữ liệu phụ trợ
        Set<UUID> categoryIds = products.stream().map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Category> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

        Set<UUID> brandIds = products.stream().map(Product::getBrandId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Brand> brandMap = brandRepository.findAllById(brandIds).stream()
                .collect(Collectors.toMap(Brand::getBrandId, Function.identity()));

        List<ProductVariant> allVariants = variantRepository.findByProductIdIn(productIds);
        Map<UUID, List<ProductVariant>> variantsByProductId = allVariants.stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .collect(Collectors.groupingBy(ProductVariant::getProductId));

        // Lấy tồn kho thời gian thực từ module Inventory qua InventoryApi
        List<UUID> variantIds = allVariants.stream().map(ProductVariant::getVariantId).toList();
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);

        List<ProductImage> allImages = imageRepository.findByProductIdIn(productIds);
        Map<UUID, List<ProductImage>> imagesByProductId = allImages.stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        // 5. Chuyển đổi sang danh sách Product Card
        List<StorefrontProductCardResponse> cards = products.stream().map(product -> {
            Category cat = categoryMap.get(product.getCategoryId());
            Brand br = brandMap.get(product.getBrandId());
            List<ProductVariant> variants = variantsByProductId.getOrDefault(product.getProductId(), Collections.emptyList());
            List<ProductImage> images = imagesByProductId.getOrDefault(product.getProductId(), Collections.emptyList());

            String primaryUrl = images.stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElseGet(() -> images.isEmpty() ? null : images.get(0).getImageUrl());

            BigDecimal minPrice = variants.stream()
                    .map(ProductVariant::getSellingPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            BigDecimal maxPrice = variants.stream()
                    .map(ProductVariant::getSellingPrice)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            // Tính tổng tồn kho khả dụng thời gian thực
            BigDecimal totalAvailableStock = variants.stream()
                    .map(v -> {
                        StockSummaryDto stock = stockMap.get(v.getVariantId());
                        return stock != null && stock.availableQty() != null ? stock.availableQty() : BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean isPreOrder = product.getSpecifications() != null && (
                    Boolean.TRUE.equals(product.getSpecifications().get("isPreOrder")) ||
                    "true".equalsIgnoreCase(String.valueOf(product.getSpecifications().get("preOrder"))) ||
                    "true".equalsIgnoreCase(String.valueOf(product.getSpecifications().get("allowPreOrder")))
            );

            String stockStatus;
            String stockBadge;
            if (totalAvailableStock.compareTo(BigDecimal.ZERO) > 0) {
                stockStatus = "IN_STOCK";
                stockBadge = "In Stock";
            } else if (isPreOrder) {
                stockStatus = "PRE_ORDER";
                stockBadge = "Pre-order";
            } else {
                stockStatus = "OUT_OF_STOCK";
                stockBadge = "Out of Stock";
            }

            return StorefrontProductCardResponse.builder()
                    .productId(product.getProductId())
                    .productCode(product.getProductCode())
                    .productName(product.getProductName())
                    .categoryId(product.getCategoryId())
                    .categoryName(cat != null ? cat.getCategoryName() : null)
                    .brandId(product.getBrandId())
                    .brandName(br != null ? br.getBrandName() : null)
                    .thumbnailUrl(primaryUrl)
                    .startingPrice(minPrice)
                    .maxPrice(maxPrice)
                    .stockStatus(stockStatus)
                    .stockBadge(stockBadge)
                    .totalAvailableStock(totalAvailableStock)
                    .totalVariants(variants.size())
                    .specificationsSummary(product.getSpecifications())
                    .createdAt(product.getCreatedAt())
                    .build();
        }).toList();

        return StorefrontSearchResultResponse.builder()
                .products(new PageImpl<>(cards, pageable, productPage.getTotalElements()))
                .message("Search completed successfully")
                .popularCategories(getPopularCategories())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductFacetResponse getFacets() {
        // 1. Categories facet kèm đếm số lượng sản phẩm ACTIVE
        List<Category> activeCategories = categoryRepository.findAllActive();
        List<ProductFacetResponse.CategoryFacetItem> categoryFacets = activeCategories.stream().map(c -> {
            Specification<Product> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), ProductStatus.ACTIVE),
                    cb.equal(root.get("categoryId"), c.getCategoryId())
            );
            long count = productRepository.count(spec);
            return ProductFacetResponse.CategoryFacetItem.builder()
                    .categoryId(c.getCategoryId())
                    .categoryCode(c.getCategoryCode())
                    .categoryName(c.getCategoryName())
                    .productCount(count)
                    .build();
        }).toList();

        // 2. Brands facet kèm đếm số lượng sản phẩm ACTIVE
        List<Brand> activeBrands = brandRepository.findAllActive();
        List<ProductFacetResponse.BrandFacetItem> brandFacets = activeBrands.stream().map(b -> {
            Specification<Product> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), ProductStatus.ACTIVE),
                    cb.equal(root.get("brandId"), b.getBrandId())
            );
            long count = productRepository.count(spec);
            return ProductFacetResponse.BrandFacetItem.builder()
                    .brandId(b.getBrandId())
                    .brandCode(b.getBrandCode())
                    .brandName(b.getBrandName())
                    .productCount(count)
                    .build();
        }).toList();

        // 3. Khoảng giá Min - Max
        BigDecimal minPrice = variantRepository.findMinActiveSellingPrice();
        BigDecimal maxPrice = variantRepository.findMaxActiveSellingPrice();

        // 4. Danh sách Màu sắc & Kích thước biến thể
        List<String> colors = variantRepository.findDistinctActiveColors();
        List<String> sizes = variantRepository.findDistinctActiveSizes();

        // 5. Thông số kỹ thuật mặt định cho thiết bị vệ sinh (Flushing type, Rough-in dimension, Ceramic glaze)
        Map<String, List<String>> techSpecs = new LinkedHashMap<>();
        techSpecs.put("flushingType", List.of("Xả xoáy Tornado", "Xả thẳng Siphon", "Xả kép 2 nhấn", "Xả cảm ứng"));
        techSpecs.put("roughInDimension", List.of("305 mm", "400 mm", "200 mm"));
        techSpecs.put("glazeTechnology", List.of("CeFiONtect (Chống bám bẩn)", "Aqua Ceramic", "Nano nung kháng khuẩn"));

        return ProductFacetResponse.builder()
                .categories(categoryFacets)
                .brands(brandFacets)
                .minPrice(minPrice != null ? minPrice : BigDecimal.ZERO)
                .maxPrice(maxPrice != null ? maxPrice : BigDecimal.ZERO)
                .colors(colors != null ? colors : Collections.emptyList())
                .sizes(sizes != null ? sizes : Collections.emptyList())
                .technicalSpecifications(techSpecs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StorefrontProductDetailResponse getProductDetail(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND, productId));

        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        Brand brand = product.getBrandId() != null ? brandRepository.findById(product.getBrandId()).orElse(null) : null;

        List<ProductVariant> variants = variantRepository.findByProductId(productId).stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .toList();

        List<ProductImage> allImages = imageRepository.findByProductId(productId);

        // Kiểm tra phân quyền: Caller là Sales Staff / Admin hay Khách hàng
        boolean isSalesStaff = iamApi.isSalesStaffOrAdmin();

        // Lấy tồn kho thời gian thực từ Inventory
        List<UUID> variantIds = variants.stream().map(ProductVariant::getVariantId).toList();
        Map<UUID, StockSummaryDto> stockMap = inventoryApi.getStockSummaries(variantIds);

        Map<UUID, List<ProductImage>> imagesByVariantId = allImages.stream()
                .filter(img -> img.getVariantId() != null)
                .collect(Collectors.groupingBy(ProductImage::getVariantId));

        // Xây dựng Variant Selector items kèm real-time stock
        List<StorefrontVariantDetailResponse> variantResponses = variants.stream().map(v -> {
            StockSummaryDto stock = stockMap.get(v.getVariantId());
            BigDecimal availableQty = stock != null && stock.availableQty() != null ? stock.availableQty() : BigDecimal.ZERO;

            boolean inStock = availableQty.compareTo(BigDecimal.ZERO) > 0;
            String stockStatus = inStock ? "IN_STOCK" : "OUT_OF_STOCK";

            // Step 6 Alternative Flow: Out of stock badge & buttons
            String stockBadge = inStock ? "In Stock" : "Temporarily Out of Stock - Contact Sales for Reservation";
            boolean canInstantBuy = inStock;
            boolean allowNotifyOrQuote = !inStock;

            // Dành cho Sales Staff: Cung cấp chi tiết mức tồn kho theo kho
            List<WarehouseStockItemResponse> warehouseBreakdown = null;
            if (isSalesStaff && stock != null) {
                warehouseBreakdown = List.of(WarehouseStockItemResponse.builder()
                        .warehouseCode(stock.warehouseCode() != null ? stock.warehouseCode() : "MAIN_WH")
                        .onHandQty(stock.onHandQty() != null ? stock.onHandQty() : BigDecimal.ZERO)
                        .reservedQty(stock.reservedQty() != null ? stock.reservedQty() : BigDecimal.ZERO)
                        .availableQty(stock.availableQty() != null ? stock.availableQty() : BigDecimal.ZERO)
                        .minimumQty(stock.minimumQty() != null ? stock.minimumQty() : BigDecimal.ZERO)
                        .isLowStock(stock.isLowStock())
                        .build());
            }

            List<ProductImageResponse> vImages = imagesByVariantId.getOrDefault(v.getVariantId(), Collections.emptyList()).stream()
                    .map(this::toImageResponse)
                    .toList();

            return StorefrontVariantDetailResponse.builder()
                    .variantId(v.getVariantId())
                    .productId(v.getProductId())
                    .sku(v.getSku())
                    .variantName(v.getVariantName())
                    .unit(v.getUnit())
                    .conversionRate(v.getConversionRate())
                    .color(v.getColor())
                    .size(v.getSize())
                    .weight(v.getWeight())
                    .technicalSpec(v.getTechnicalSpec())
                    .sellingPrice(v.getSellingPrice())
                    .warrantyPolicyId(v.getWarrantyPolicyId())
                    .serialTracking(v.getSerialTracking())
                    .status(v.getStatus())
                    .images(vImages)
                    .stockStatus(stockStatus)
                    .stockBadge(stockBadge)
                    .availableQuantity(availableQty)
                    .canInstantBuy(canInstantBuy)
                    .allowNotifyOrQuote(allowNotifyOrQuote)
                    .isSalesStaff(isSalesStaff)
                    .warehouseBreakdown(warehouseBreakdown)
                    .createdAt(v.getCreatedAt())
                    .updatedAt(v.getUpdatedAt())
                    .build();
        }).toList();

        // Hình ảnh sản phẩm chung
        List<ProductImageResponse> productImages = allImages.stream()
                .filter(img -> img.getVariantId() == null)
                .map(this::toImageResponse)
                .toList();

        // Starting price
        BigDecimal startingPrice = variants.stream()
                .map(ProductVariant::getSellingPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        boolean isPreOrder = product.getSpecifications() != null && (
                Boolean.TRUE.equals(product.getSpecifications().get("isPreOrder")) ||
                "true".equalsIgnoreCase(String.valueOf(product.getSpecifications().get("preOrder"))) ||
                "true".equalsIgnoreCase(String.valueOf(product.getSpecifications().get("allowPreOrder")))
        );
        boolean anyInStock = variantResponses.stream().anyMatch(v -> v.availableQuantity().compareTo(BigDecimal.ZERO) > 0);
        String overallStockStatus = anyInStock ? "IN_STOCK" : (isPreOrder ? "PRE_ORDER" : "OUT_OF_STOCK");
        String overallStockBadge = anyInStock ? "In Stock" : (isPreOrder ? "Pre-order" : "Out of Stock");

        // CAD Blueprints & Installation Manual
        List<String> cadBlueprints = extractCadBlueprints(product, allImages);
        String manualPdf = extractInstallationManualPdf(product);
        String warrantyTerms = extractWarrantyTerms(product);

        return StorefrontProductDetailResponse.builder()
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getProductName())
                .category(category != null ? CategoryBriefResponse.builder()
                        .categoryId(category.getCategoryId())
                        .categoryCode(category.getCategoryCode())
                        .categoryName(category.getCategoryName())
                        .build() : null)
                .brand(brand != null ? BrandBriefResponse.builder()
                        .brandId(brand.getBrandId())
                        .brandCode(brand.getBrandCode())
                        .brandName(brand.getBrandName())
                        .build() : null)
                .description(product.getDescription())
                .specifications(product.getSpecifications())
                .productType(product.getProductType())
                .status(product.getStatus())
                .startingPrice(startingPrice)
                .stockStatus(overallStockStatus)
                .stockBadge(overallStockBadge)
                .images(productImages)
                .cadBlueprints(cadBlueprints)
                .installationManualPdf(manualPdf)
                .warrantyTerms(warrantyTerms)
                .variants(variantResponses)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Set<UUID> findInStockProductIds() {
        List<ProductVariant> activeVariants = variantRepository.findAll().stream()
                .filter(v -> v.getStatus() == VariantStatus.ACTIVE)
                .toList();
        List<UUID> vIds = activeVariants.stream().map(ProductVariant::getVariantId).toList();
        Map<UUID, StockSummaryDto> stocks = inventoryApi.getStockSummaries(vIds);

        return activeVariants.stream()
                .filter(v -> {
                    StockSummaryDto s = stocks.get(v.getVariantId());
                    return s != null && s.availableQty() != null && s.availableQty().compareTo(BigDecimal.ZERO) > 0;
                })
                .map(ProductVariant::getProductId)
                .collect(Collectors.toSet());
    }

    private Set<UUID> intersect(Set<UUID> current, Set<UUID> filterResult) {
        if (current == null) {
            return new HashSet<>(filterResult);
        }
        current.retainAll(filterResult);
        return current;
    }

    private List<CategoryBriefResponse> getPopularCategories() {
        return categoryRepository.findAllActive().stream()
                .limit(5)
                .map(c -> CategoryBriefResponse.builder()
                        .categoryId(c.getCategoryId())
                        .categoryCode(c.getCategoryCode())
                        .categoryName(c.getCategoryName())
                        .build())
                .toList();
    }

    private List<String> extractCadBlueprints(Product product, List<ProductImage> allImages) {
        List<String> list = new ArrayList<>();
        if (product.getSpecifications() != null && product.getSpecifications().containsKey("cadBlueprintUrl")) {
            list.add(String.valueOf(product.getSpecifications().get("cadBlueprintUrl")));
        }
        // Thêm hình ảnh nếu có chứa từ khóa blueprint/cad
        for (ProductImage img : allImages) {
            if (img.getImageUrl() != null && (img.getImageUrl().toLowerCase().contains("cad") || img.getImageUrl().toLowerCase().contains("blueprint"))) {
                list.add(img.getImageUrl());
            }
        }
        return list;
    }

    private String extractInstallationManualPdf(Product product) {
        if (product.getSpecifications() != null && product.getSpecifications().containsKey("installationManualPdf")) {
            return String.valueOf(product.getSpecifications().get("installationManualPdf"));
        }
        return "https://aquasuite.example.com/docs/manuals/" + product.getProductCode() + "-installation-guide.pdf";
    }

    private String extractWarrantyTerms(Product product) {
        if (product.getSpecifications() != null && product.getSpecifications().containsKey("warrantyTerms")) {
            return String.valueOf(product.getSpecifications().get("warrantyTerms"));
        }
        return "10-year ceramic warranty, 2-year electronic valve warranty";
    }

    private ProductImageResponse toImageResponse(ProductImage img) {
        return ProductImageResponse.builder()
                .imageId(img.getImageId())
                .variantId(img.getVariantId())
                .imageUrl(img.getImageUrl())
                .isPrimary(img.getIsPrimary())
                .sortOrder(img.getSortOrder())
                .build();
    }
}
