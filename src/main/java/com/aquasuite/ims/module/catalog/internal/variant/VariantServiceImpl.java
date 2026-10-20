package com.aquasuite.ims.module.catalog.internal.variant;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.api.event.ProductVariantChangedEvent;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import com.aquasuite.ims.module.catalog.internal.variant.dto.*;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.VariantInventoryUsage;
import com.aquasuite.ims.module.sales.api.SalesVariantUsageApi;
import com.aquasuite.ims.module.sales.api.dto.VariantDocumentUsage;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VariantServiceImpl implements VariantService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryApi inventoryApi;
    private final SalesVariantUsageApi salesVariantUsageApi;
    private final JdbcTemplate jdbcTemplate;
    private final IamApi iamApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<VariantResponse> list(UUID productId, String keyword, String status, Pageable pageable) {
        requireProduct(productId);
        return variantRepository.search(productId, blankToNull(keyword), parseStatus(status), pageable).map(this::response);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantStatsResponse stats(UUID productId) {
        requireProduct(productId);
        return new VariantStatsResponse(variantRepository.countByProductIdAndStatusNot(productId, VariantStatus.DELETED),
                variantRepository.countByProductIdAndStatus(productId, VariantStatus.ACTIVE),
                variantRepository.countByProductIdAndStatus(productId, VariantStatus.INACTIVE),
                variantRepository.countByProductIdAndStatusNotAndSellingPriceIsNull(productId, VariantStatus.DELETED));
    }

    @Override
    @Transactional
    public VariantResponse create(UUID productId, VariantUpsertRequest request) {
        requireProduct(productId);
        ProductVariant variant = new ProductVariant();
        apply(request, variant);
        variant.setProductId(productId);
        ensureUniqueSku(request.sku(), null);
        persist(variant);
        inventoryApi.initializeMainWarehouseInventory(variant.getVariantId());
        publish(variant, "CREATED", Map.of(), null);
        return response(variant);
    }

    @Override
    @Transactional
    public List<VariantResponse> createMatrix(UUID productId, VariantMatrixRequest request) {
        requireProduct(productId);
        Set<String> inputSkus = new HashSet<>();
        for (VariantUpsertRequest item : request.variants()) {
            String sku = normalizeSku(item.sku());
            if (!inputSkus.add(sku.toUpperCase(Locale.ROOT)) || variantRepository.existsBySkuIgnoreCase(sku)) {
                throw new AppException(CatalogVariantErrorCode.DUPLICATE_SKU);
            }
        }
        List<ProductVariant> saved = new ArrayList<>();
        for (VariantUpsertRequest item : request.variants()) {
            ProductVariant variant = new ProductVariant();
            apply(item, variant); variant.setProductId(productId); persist(variant);
            inventoryApi.initializeMainWarehouseInventory(variant.getVariantId()); publish(variant, "CREATED", Map.of(), null); saved.add(variant);
        }
        return saved.stream().map(this::response).toList();
    }

    @Override
    @Transactional
    public VariantResponse update(UUID productId, UUID variantId, VariantUpsertRequest request) {
        ProductVariant variant = requireVariant(productId, variantId);
        ensureUniqueSku(request.sku(), variantId);
        Map<String, Object> oldData = snapshot(variant); apply(request, variant); persist(variant);
        publish(variant, "UPDATED", oldData, null);
        return response(variant);
    }

    @Override
    @Transactional
    public VariantResponse changeStatus(UUID productId, UUID variantId, VariantStatusRequest request) {
        ProductVariant variant = requireVariant(productId, variantId);
        if (request.status() == VariantStatus.DELETED) throw new AppException(CatalogVariantErrorCode.INVALID_STATUS);
        if (request.status() == VariantStatus.ACTIVE && variant.getSellingPrice() == null) throw new AppException(CatalogVariantErrorCode.RETAIL_PRICE_REQUIRED);
        Map<String, Object> oldData = snapshot(variant); variant.setStatus(request.status());
        ProductVariant saved = variantRepository.save(variant); publish(saved, "STATUS_CHANGED", oldData, null);
        return response(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantDeletionEligibilityResponse deletionEligibility(UUID productId, UUID variantId) {
        requireVariant(productId, variantId);
        return usage(variantId);
    }

    @Override
    @Transactional
    public void delete(UUID productId, UUID variantId, VariantDeleteRequest request) {
        if (!request.confirmed()) throw new AppException(CatalogVariantErrorCode.INVALID_DELETE_CONFIRMATION);
        ProductVariant variant = requireVariant(productId, variantId);
        Map<String, Object> deletedSnapshot = snapshot(variant);
        VariantDeletionEligibilityResponse usage = usage(variantId);
        if (!usage.eligible()) throw new VariantDeleteBlockedException(usage);
        salesVariantUsageApi.removeVariantFromCarts(variantId);
        jdbcTemplate.update("DELETE FROM public.product_images WHERE variant_id = ?", variantId);
        jdbcTemplate.update("DELETE FROM public.bundle_items WHERE variant_id = ?", variantId);
        inventoryApi.deleteEmptyVariantInventory(variantId);
        variantRepository.delete(variant);
        variantRepository.flush();
        eventPublisher.publishEvent(new ProductVariantChangedEvent(iamApi.getCurrentUserId(), variantId, "DELETED", deletedSnapshot, Map.of(), request.reason()));
    }

    private VariantDeletionEligibilityResponse usage(UUID variantId) {
        VariantInventoryUsage inventory = inventoryApi.getVariantDeletionUsage(variantId);
        VariantDocumentUsage documents = salesVariantUsageApi.getVariantDocumentUsage(variantId);
        Long activeBundles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.bundle_items bi JOIN public.products p ON p.product_id = bi.bundle_product_id WHERE bi.variant_id = ? AND p.status = 'ACTIVE'", Long.class, variantId);
        long bundleCount = activeBundles == null ? 0 : activeBundles;
        boolean eligible = inventory.canDelete() && !documents.hasBlockingDocument() && bundleCount == 0;
        return new VariantDeletionEligibilityResponse(variantId, inventory.onHandQty(), inventory.reservedQty(),
                documents.goodsReceiptCount(), inventory.stockMovementCount(), documents.dispatchCount(), documents.quotationCount(),
                documents.orderCount(), documents.invoiceCount(), inventory.stockCountItemCount(), bundleCount,
                documents.cartItemCount(), eligible);
    }

    private void apply(VariantUpsertRequest request, ProductVariant target) {
        BigDecimal cost = request.costPrice();
        BigDecimal retail = request.sellingPrice();
        if (request.status() == VariantStatus.DELETED) {
            throw new AppException(CatalogVariantErrorCode.INVALID_STATUS);
        }
        if (cost != null && retail != null && retail.compareTo(cost) < 0 && !request.acknowledgeBelowCost()) {
            throw new AppException(CatalogVariantErrorCode.PRICE_WARNING_ACK_REQUIRED);
        }
        target.setSku(normalizeSku(request.sku())); target.setVariantName(request.variantName().trim()); target.setUnit(request.unit().trim());
        target.setConversionRate(request.conversionRate()); target.setColor(blankToNull(request.color())); target.setSize(blankToNull(request.size()));
        target.setWeight(request.weight()); target.setTechnicalSpec(request.technicalSpec()); target.setCostPrice(cost == null ? BigDecimal.ZERO : cost);
        target.setSellingPrice(retail); target.setWarrantyPolicyId(request.warrantyPolicyId()); target.setSerialTracking(Boolean.TRUE.equals(request.serialTracking()));
        target.setStatus(retail == null ? VariantStatus.INACTIVE : request.status() == null ? VariantStatus.ACTIVE : request.status());
    }

    private void ensureUniqueSku(String sku, UUID currentId) {
        variantRepository.findBySkuIgnoreCase(normalizeSku(sku)).filter(existing -> !existing.getVariantId().equals(currentId))
                .ifPresent(existing -> { throw new AppException(CatalogVariantErrorCode.DUPLICATE_SKU); });
    }
    private void persist(ProductVariant variant) {
        try { variantRepository.saveAndFlush(variant); }
        catch (DataIntegrityViolationException exception) { throw new AppException(CatalogVariantErrorCode.DUPLICATE_SKU); }
    }
    private void requireProduct(UUID productId) {
        productRepository.findById(productId).filter(product -> product.getStatus() != ProductStatus.DELETED)
                .orElseThrow(() -> new AppException(CatalogVariantErrorCode.PRODUCT_NOT_FOUND));
    }
    private ProductVariant requireVariant(UUID productId, UUID variantId) {
        requireProduct(productId);
        return variantRepository.findById(variantId).filter(variant -> variant.getProductId().equals(productId))
                .orElseThrow(() -> new AppException(CatalogVariantErrorCode.VARIANT_NOT_FOUND));
    }
    private VariantResponse response(ProductVariant value) {
        return new VariantResponse(value.getVariantId(), value.getProductId(), value.getSku(), value.getVariantName(), value.getUnit(),
                value.getConversionRate(), value.getColor(), value.getSize(), value.getWeight(), value.getTechnicalSpec(), value.getCostPrice(),
                value.getSellingPrice(), value.getWarrantyPolicyId(), Boolean.TRUE.equals(value.getSerialTracking()), value.getStatus().name());
    }
    private Map<String, Object> snapshot(ProductVariant value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sku", value.getSku()); data.put("variantName", value.getVariantName()); data.put("status", value.getStatus().name());
        data.put("costPrice", value.getCostPrice()); data.put("sellingPrice", value.getSellingPrice()); data.put("productId", value.getProductId());
        return data;
    }
    private void publish(ProductVariant value, String action, Map<String, Object> oldData, String reason) {
        eventPublisher.publishEvent(new ProductVariantChangedEvent(iamApi.getCurrentUserId(), value.getVariantId(), action, oldData, snapshot(value), reason));
    }
    private String normalizeSku(String value) { return value == null ? "" : value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private VariantStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            VariantStatus status = VariantStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status == VariantStatus.DELETED) throw new AppException(CatalogVariantErrorCode.INVALID_STATUS);
            return status;
        }
        catch (IllegalArgumentException exception) { throw new AppException(CatalogVariantErrorCode.INVALID_STATUS); }
    }
}
