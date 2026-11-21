package com.aquasuite.ims.service.catalog.impl;

import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.*;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.enums.catalog.VariantStatus;
import com.aquasuite.ims.service.catalog.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductShopOwnerServiceImpl productShopOwnerService;
    private final VariantServiceImpl variantService;

    // ── Product Operations Delegations ───────────────────────────────────────

    @Override
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        return productShopOwnerService.createProduct(request);
    }

    @Override
    public Page<ProductSummaryResponse> listProducts(String keyword, UUID categoryId, UUID brandId,
            ProductStatus status, ProductType productType, Pageable pageable) {
        return productShopOwnerService.listProducts(keyword, categoryId, brandId, status, productType, pageable);
    }

    @Override
    public ProductDetailResponse getProductDetail(UUID productId) {
        return productShopOwnerService.getProductDetail(productId);
    }

    @Override
    public ProductDetailResponse updateProduct(UUID productId, UpdateProductRequest request) {
        return productShopOwnerService.updateProduct(productId, request);
    }

    @Override
    public ProductDetailResponse updateProductStatus(UUID productId, UpdateProductStatusRequest request) {
        return productShopOwnerService.updateProductStatus(productId, request);
    }

    @Override
    public void deleteProduct(UUID productId) {
        productShopOwnerService.deleteProduct(productId);
    }

    // ── Variant (SKU) Operations Delegations ─────────────────────────────────

    @Override
    public Page<VariantResponse> list(UUID productId, String keyword, VariantStatus status, boolean missingPrice,
                                      String sortBy, String sortDir, String warehouseCode, Pageable pageable) {
        return variantService.list(productId, keyword, status, missingPrice, sortBy, sortDir, warehouseCode, pageable);
    }

    @Override
    public VariantStatsResponse stats(UUID productId) {
        return variantService.stats(productId);
    }

    @Override
    public VariantResponse create(UUID productId, VariantUpsertRequest request) {
        return variantService.create(productId, request);
    }

    @Override
    public List<VariantResponse> createMatrix(UUID productId, VariantMatrixRequest request) {
        return variantService.createMatrix(productId, request);
    }

    @Override
    public VariantResponse update(UUID productId, UUID variantId, VariantUpsertRequest request) {
        return variantService.update(productId, variantId, request);
    }

    @Override
    public VariantResponse changeStatus(UUID productId, UUID variantId, VariantStatusRequest request) {
        return variantService.changeStatus(productId, variantId, request);
    }

    @Override
    public VariantDeletionEligibilityResponse deletionEligibility(UUID productId, UUID variantId) {
        return variantService.deletionEligibility(productId, variantId);
    }

    @Override
    public void delete(UUID productId, UUID variantId, VariantDeleteRequest request) {
        variantService.delete(productId, variantId, request);
    }
}
