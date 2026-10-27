package com.aquasuite.ims.module.catalog.internal.service.impl;

import com.aquasuite.ims.module.catalog.internal.entity.ProductVariant;
import com.aquasuite.ims.module.catalog.api.event.ProductVariantChangedEvent;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.mapper.VariantMapper;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantDeleteRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantMatrixRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantUpsertRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantDeletionEligibilityResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantStatsResponse;
import com.aquasuite.ims.module.catalog.internal.repository.ProductRepository;
import com.aquasuite.ims.module.catalog.internal.repository.ProductVariantRepository;
import com.aquasuite.ims.module.catalog.internal.service.VariantService;
import com.aquasuite.ims.module.catalog.internal.error.CatalogVariantErrorCode;
import com.aquasuite.ims.module.catalog.internal.error.VariantDeleteBlockedException;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.VariantInventoryUsage;
import com.aquasuite.ims.module.sales.api.SalesVariantUsageApi;
import com.aquasuite.ims.module.sales.api.dto.VariantDocumentUsage;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VariantServiceImpl implements VariantService {
    ProductRepository productRepository;
    ProductVariantRepository variantRepository;
    InventoryApi inventoryApi;
    SalesVariantUsageApi salesVariantUsageApi;
    JdbcTemplate jdbcTemplate;
    IamApi iamApi;
    ApplicationEventPublisher eventPublisher;
    VariantMapper variantMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<VariantResponse> list(UUID productId, String keyword, VariantStatus status, Pageable pageable) {
        requireProduct(productId);
        String searchKeyword = (keyword == null || keyword.trim().isEmpty())
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        Page<ProductVariant> result = variantRepository.search(productId, searchKeyword, status, pageable);
        return result.map(variantMapper::toResponse);
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
            apply(item, variant);
            variant.setProductId(productId);
            persist(variant);
            inventoryApi.initializeMainWarehouseInventory(variant.getVariantId());
            publish(variant, "CREATED", Map.of(), null);
            saved.add(variant);
        }
        return saved.stream().map(this::response).toList();
    }

    @Override
    @Transactional
    public VariantResponse update(UUID productId, UUID variantId, VariantUpsertRequest request) {
        ProductVariant variant = requireVariant(productId, variantId);
        ensureUniqueSku(request.sku(), variantId);
        Map<String, Object> oldData = snapshot(variant);
        apply(request, variant);
        persist(variant);
        publish(variant, "UPDATED", oldData, null);
        return response(variant);
    }

    @Override
    @Transactional
    public VariantResponse changeStatus(UUID productId, UUID variantId, VariantStatusRequest request) {
        ProductVariant variant = requireVariant(productId, variantId);
        if (request.status() == VariantStatus.DELETED) throw new AppException(CatalogVariantErrorCode.INVALID_STATUS);
        if (request.status() == VariantStatus.ACTIVE && variant.getSellingPrice() == null)
            throw new AppException(CatalogVariantErrorCode.RETAIL_PRICE_REQUIRED);
        Map<String, Object> oldData = snapshot(variant);
        variant.setStatus(request.status());
        ProductVariant saved = variantRepository.save(variant);
        publish(saved, "STATUS_CHANGED", oldData, null);
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
        ProductVariant variant = requireVariantForUpdate(productId, variantId);
        Map<String, Object> deletedSnapshot = snapshot(variant);
        VariantDeletionEligibilityResponse usage = usage(variantId, true);
        if (!usage.eligible()) throw new VariantDeleteBlockedException(usage);
        deletedSnapshot.put("deletionEligibility", deletionEligibilitySnapshot(usage));
        salesVariantUsageApi.removeVariantFromCarts(variantId);
        jdbcTemplate.update("DELETE FROM public.product_images WHERE variant_id = ?", variantId);
        jdbcTemplate.update("DELETE FROM public.bundle_items WHERE variant_id = ?", variantId);
        removeInactiveBundleComponentReferences(variantId);
        inventoryApi.deleteEmptyVariantInventory(variantId);
        variantRepository.delete(variant);
        variantRepository.flush();
        eventPublisher.publishEvent(new ProductVariantChangedEvent(iamApi.getCurrentUserId(), variantId, "DELETED", deletedSnapshot, Map.of(), request.reason()));
    }

    private VariantDeletionEligibilityResponse usage(UUID variantId) {
        return usage(variantId, false);
    }

    private VariantDeletionEligibilityResponse usage(UUID variantId, boolean lockInventory) {
        VariantInventoryUsage inventory = lockInventory
                ? inventoryApi.lockAndGetVariantDeletionUsage(variantId)
                : inventoryApi.getVariantDeletionUsage(variantId);
        VariantDocumentUsage documents = salesVariantUsageApi.getVariantDocumentUsage(variantId);
        long bundleCount = countActiveBundleItems(variantId);
        long bundleOptionCount = countActiveBundleOptions(variantId);
        boolean eligible = inventory.canDelete() && !documents.hasBlockingDocument()
                && bundleCount == 0 && bundleOptionCount == 0;
        return new VariantDeletionEligibilityResponse(variantId, inventory.onHandQty(), inventory.reservedQty(),
                documents.goodsReceiptCount(), inventory.stockMovementCount(), documents.dispatchCount(), documents.quotationCount(),
                documents.orderCount(), documents.invoiceCount(), inventory.stockCountItemCount(), bundleCount,
                bundleOptionCount, documents.purchaseOrderCount(), documents.deliveryItemCount(), documents.cartItemCount(), eligible);
    }

    private long countActiveBundleItems(UUID variantId) {
        return count("SELECT COUNT(*) FROM public.bundle_items item JOIN public.products product "
                + "ON product.product_id = item.bundle_product_id WHERE item.variant_id = ? AND product.status = 'ACTIVE'", variantId);
    }

    private long countActiveBundleOptions(UUID variantId) {
        return count("SELECT COUNT(*) FROM public.bundle_component_groups component "
                + "JOIN public.products product ON product.product_id = component.bundle_product_id "
                + "WHERE product.status = 'ACTIVE' AND (component.default_variant_id = ? OR ? = ANY(component.allowed_variant_ids))",
                variantId, variantId);
    }

    private long count(String sql, Object... parameters) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return result == null ? 0 : result;
    }

    private void removeInactiveBundleComponentReferences(UUID variantId) {
        jdbcTemplate.update("DELETE FROM public.bundle_component_groups component USING public.products product "
                + "WHERE component.bundle_product_id = product.product_id AND product.status <> 'ACTIVE' "
                + "AND component.default_variant_id = ?", variantId);
        jdbcTemplate.update("UPDATE public.bundle_component_groups component SET allowed_variant_ids = array_remove(component.allowed_variant_ids, ?) "
                + "FROM public.products product WHERE component.bundle_product_id = product.product_id "
                + "AND product.status <> 'ACTIVE' AND component.default_variant_id <> ? "
                + "AND ? = ANY(component.allowed_variant_ids)", variantId, variantId, variantId);
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
        target.setSku(normalizeSku(request.sku()));
        target.setVariantName(request.variantName().trim());
        target.setUnit(request.unit().trim());
        target.setConversionRate(request.conversionRate());
        target.setColor(blankToNull(request.color()));
        target.setSize(blankToNull(request.size()));
        target.setWeight(request.weight());
        target.setTechnicalSpec(request.technicalSpec());
        target.setCostPrice(cost == null ? BigDecimal.ZERO : cost);
        target.setSellingPrice(retail);
        target.setWarrantyPolicyId(request.warrantyPolicyId());
        target.setSerialTracking(Boolean.TRUE.equals(request.serialTracking()));
        target.setStatus(retail == null ? VariantStatus.INACTIVE : request.status() == null ? VariantStatus.ACTIVE : request.status());
    }

    private void ensureUniqueSku(String sku, UUID currentId) {
        variantRepository.findBySkuIgnoreCase(normalizeSku(sku)).filter(existing -> !existing.getVariantId().equals(currentId))
                .ifPresent(existing -> {
                    throw new AppException(CatalogVariantErrorCode.DUPLICATE_SKU);
                });
    }

    private void persist(ProductVariant variant) {
        try {
            variantRepository.saveAndFlush(variant);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(CatalogVariantErrorCode.DUPLICATE_SKU);
        }
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

    private ProductVariant requireVariantForUpdate(UUID productId, UUID variantId) {
        requireProduct(productId);
        return variantRepository.findByProductIdAndVariantIdForUpdate(productId, variantId)
                .orElseThrow(() -> new AppException(CatalogVariantErrorCode.VARIANT_NOT_FOUND));
    }

    private VariantResponse response(ProductVariant value) {
        return new VariantResponse(value.getVariantId(), value.getProductId(), value.getSku(), value.getVariantName(), value.getUnit(),
                value.getConversionRate(), value.getColor(), value.getSize(), value.getWeight(), value.getTechnicalSpec(), value.getCostPrice(),
                value.getSellingPrice(), value.getWarrantyPolicyId(), Boolean.TRUE.equals(value.getSerialTracking()), value.getStatus().name());
    }

    private Map<String, Object> snapshot(ProductVariant value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sku", value.getSku());
        data.put("variantName", value.getVariantName());
        data.put("status", value.getStatus().name());
        data.put("costPrice", value.getCostPrice());
        data.put("sellingPrice", value.getSellingPrice());
        data.put("productId", value.getProductId());
        return data;
    }

    private Map<String, Object> deletionEligibilitySnapshot(VariantDeletionEligibilityResponse usage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onHandQty", usage.onHandQty());
        data.put("reservedQty", usage.reservedQty());
        data.put("goodsReceiptCount", usage.goodsReceiptCount());
        data.put("stockMovementCount", usage.stockMovementCount());
        data.put("dispatchCount", usage.dispatchCount());
        data.put("quotationCount", usage.quotationCount());
        data.put("orderCount", usage.orderCount());
        data.put("invoiceCount", usage.invoiceCount());
        data.put("stockCountItemCount", usage.stockCountItemCount());
        data.put("activeBundleCount", usage.activeBundleCount());
        data.put("activeBundleOptionCount", usage.activeBundleOptionCount());
        data.put("purchaseOrderCount", usage.purchaseOrderCount());
        data.put("deliveryItemCount", usage.deliveryItemCount());
        data.put("cartItemCount", usage.cartItemCount());
        return data;
    }

    private void publish(ProductVariant value, String action, Map<String, Object> oldData, String reason) {
        eventPublisher.publishEvent(new ProductVariantChangedEvent(iamApi.getCurrentUserId(), value.getVariantId(), action, oldData, snapshot(value), reason));
    }

    private String normalizeSku(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private VariantStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            VariantStatus status = VariantStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status == VariantStatus.DELETED) throw new AppException(CatalogVariantErrorCode.INVALID_STATUS);
            return status;
        } catch (IllegalArgumentException exception) {
            throw new AppException(CatalogVariantErrorCode.INVALID_STATUS);
        }
    }
}
