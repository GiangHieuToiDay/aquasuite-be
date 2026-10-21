package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.*;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.exception.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateProductRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateProductRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateProductStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.*;
import com.aquasuite.ims.module.catalog.internal.repository.*;
import com.aquasuite.ims.module.catalog.internal.service.ProductAdminService;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
public class ProductAdminServiceImpl implements ProductAdminService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        String productCode = request.productCode().trim();
        if (productRepository.existsByProductCode(productCode)) {
            throw new AppException(CatalogErrorCode.PRODUCT_CODE_ALREADY_EXISTS);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND, request.categoryId()));

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new AppException(CatalogErrorCode.BRAND_NOT_FOUND, request.brandId()));
        }

        // 1. Lưu Sản phẩm (SPU)
        Product product = Product.builder()
                .productCode(productCode)
                .productName(request.productName().trim())
                .categoryId(request.categoryId())
                .brandId(request.brandId())
                .description(request.description())
                .specifications(request.specifications() != null ? request.specifications() : Collections.emptyMap())
                .productType(request.productType() != null ? request.productType() : ProductType.NORMAL)
                .status(request.status() != null ? request.status() : ProductStatus.ACTIVE)
                .build();
        product = productRepository.save(product);

        // 2. Lưu hình ảnh sản phẩm (nếu có)
        List<ProductImage> allImages = new ArrayList<>();
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            for (int i = 0; i < request.imageUrls().size(); i++) {
                ProductImage img = ProductImage.builder()
                        .productId(product.getProductId())
                        .imageUrl(request.imageUrls().get(i))
                        .isPrimary(i == 0)
                        .sortOrder(i + 1)
                        .build();
                allImages.add(imageRepository.save(img));
            }
        }

        log.info("Admin created product: ID={}, Code={}", product.getProductId(), product.getProductCode());

        // Sản phẩm mới tạo chưa có biến thể (biến thể do chức năng Manage SKUs & Variants quản lý)
        return toDetailResponse(product, category, brand, Collections.emptyList(), allImages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> listProducts(
            String keyword,
            UUID categoryId,
            UUID brandId,
            ProductStatus status,
            ProductType productType,
            Pageable pageable
    ) {
        Specification<Product> spec = ProductSpecification.filter(
                keyword, categoryId, brandId, status, productType, true
        );

        Page<Product> productPage = productRepository.findAll(spec, pageable);
        if (productPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, productPage.getTotalElements());
        }

        List<Product> products = productPage.getContent();
        List<UUID> productIds = products.stream().map(Product::getProductId).toList();

        // 1. Batch load Categories
        Set<UUID> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Category> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));

        // 2. Batch load Brands
        Set<UUID> brandIds = products.stream()
                .map(Product::getBrandId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Brand> brandMap = brandRepository.findAllById(brandIds).stream()
                .collect(Collectors.toMap(Brand::getBrandId, Function.identity()));

        // 3. Batch load Variants (Chỉ đọc để tính khoảng giá min/max hiển thị nếu đã có SKU)
        List<ProductVariant> allVariants = variantRepository.findByProductIdIn(productIds);
        Map<UUID, List<ProductVariant>> variantsByProductId = allVariants.stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));

        // 4. Batch load Images
        List<ProductImage> allImages = imageRepository.findByProductIdIn(productIds);
        Map<UUID, List<ProductImage>> imagesByProductId = allImages.stream()
                .collect(Collectors.groupingBy(ProductImage::getProductId));

        // 5. Map to Summary Responses
        List<ProductSummaryResponse> summaries = products.stream().map(product -> {
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

            return ProductSummaryResponse.builder()
                    .productId(product.getProductId())
                    .productCode(product.getProductCode())
                    .productName(product.getProductName())
                    .categoryId(product.getCategoryId())
                    .categoryName(cat != null ? cat.getCategoryName() : null)
                    .brandId(product.getBrandId())
                    .brandName(br != null ? br.getBrandName() : null)
                    .productType(product.getProductType())
                    .status(product.getStatus())
                    .primaryImageUrl(primaryUrl)
                    .minSellingPrice(minPrice)
                    .maxSellingPrice(maxPrice)
                    .totalVariants(variants.size())
                    .createdAt(product.getCreatedAt())
                    .updatedAt(product.getUpdatedAt())
                    .build();
        }).toList();

        return new PageImpl<>(summaries, pageable, productPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND, productId));

        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        Brand brand = product.getBrandId() != null ? brandRepository.findById(product.getBrandId()).orElse(null) : null;

        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        List<ProductImage> images = imageRepository.findByProductId(productId);

        return toDetailResponse(product, category, brand, variants, images);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(UUID productId, UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND, productId));

        String newCode = request.productCode().trim();
        if (productRepository.existsByProductCodeAndProductIdNot(newCode, productId)) {
            throw new AppException(CatalogErrorCode.PRODUCT_CODE_ALREADY_EXISTS);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND, request.categoryId()));

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new AppException(CatalogErrorCode.BRAND_NOT_FOUND, request.brandId()));
        }

        // Cập nhật thông tin Sản phẩm cha
        product.setProductCode(newCode);
        product.setProductName(request.productName().trim());
        product.setCategoryId(request.categoryId());
        product.setBrandId(request.brandId());
        product.setDescription(request.description());
        if (request.specifications() != null) {
            product.setSpecifications(request.specifications());
        }
        if (request.productType() != null) {
            product.setProductType(request.productType());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        product = productRepository.save(product);

        // Cập nhật hình ảnh sản phẩm (nếu được truyền)
        if (request.imageUrls() != null) {
            List<ProductImage> existingImages = imageRepository.findByProductId(productId);
            for (ProductImage img : existingImages) {
                if (img.getVariantId() == null) {
                    imageRepository.delete(img);
                }
            }
            for (int i = 0; i < request.imageUrls().size(); i++) {
                ProductImage img = ProductImage.builder()
                        .productId(productId)
                        .imageUrl(request.imageUrls().get(i))
                        .isPrimary(i == 0)
                        .sortOrder(i + 1)
                        .build();
                imageRepository.save(img);
            }
        }

        List<ProductVariant> existingVariants = variantRepository.findByProductId(productId);
        List<ProductImage> allImages = imageRepository.findByProductId(productId);
        log.info("Admin updated product: ID={}, Code={}", product.getProductId(), product.getProductCode());

        return toDetailResponse(product, category, brand, existingVariants, allImages);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProductStatus(UUID productId, UpdateProductStatusRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND, productId));

        product.setStatus(request.status());
        product = productRepository.save(product);

        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        Brand brand = product.getBrandId() != null ? brandRepository.findById(product.getBrandId()).orElse(null) : null;
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        List<ProductImage> images = imageRepository.findByProductId(productId);

        log.info("Admin updated product status: ID={}, Status={}", productId, request.status());
        return toDetailResponse(product, category, brand, variants, images);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND, productId));

        product.setStatus(ProductStatus.DELETED);
        productRepository.save(product);

        log.info("Admin soft-deleted product: ID={}", productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryBriefResponse> getActiveCategories() {
        return categoryRepository.findAllActive().stream()
                .map(c -> CategoryBriefResponse.builder()
                        .categoryId(c.getCategoryId())
                        .categoryCode(c.getCategoryCode())
                        .categoryName(c.getCategoryName())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandBriefResponse> getActiveBrands() {
        return brandRepository.findAllActive().stream()
                .map(b -> BrandBriefResponse.builder()
                        .brandId(b.getBrandId())
                        .brandCode(b.getBrandCode())
                        .brandName(b.getBrandName())
                        .build())
                .toList();
    }

    // =========================================================================
    // HELPER MAPPING
    // =========================================================================

    private ProductDetailResponse toDetailResponse(
            Product product,
            Category category,
            Brand brand,
            List<ProductVariant> variants,
            List<ProductImage> allImages
    ) {
        List<ProductImageResponse> productImages = allImages.stream()
                .filter(img -> img.getVariantId() == null)
                .map(this::toImageResponse)
                .toList();

        Map<UUID, List<ProductImage>> imagesByVariantId = allImages.stream()
                .filter(img -> img.getVariantId() != null)
                .collect(Collectors.groupingBy(ProductImage::getVariantId));

        List<ProductVariantResponse> variantResponses = (variants == null ? Collections.<ProductVariant>emptyList() : variants).stream().map(v -> {
            List<ProductImageResponse> vImages = imagesByVariantId.getOrDefault(v.getVariantId(), Collections.emptyList())
                    .stream()
                    .map(this::toImageResponse)
                    .toList();

            return ProductVariantResponse.builder()
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
                    .costPrice(v.getCostPrice())
                    .sellingPrice(v.getSellingPrice())
                    .warrantyPolicyId(v.getWarrantyPolicyId())
                    .serialTracking(v.getSerialTracking())
                    .status(v.getStatus())
                    .images(vImages)
                    .createdAt(v.getCreatedAt())
                    .updatedAt(v.getUpdatedAt())
                    .build();
        }).toList();

        return ProductDetailResponse.builder()
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
                .images(productImages)
                .variants(variantResponses)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
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
