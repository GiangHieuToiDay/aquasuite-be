package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.dto.catalog.response.CatalogBundleSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogSkuSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogBrandSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogCategorySummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogVariantWarrantyReferenceDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Interface của Module Catalog.
 * Các module khác (Sales, Inventory, ServiceDesk, Purchasing, AiAdvisor, ...) giao tiếp đồng bộ với Catalog qua Interface này.
 */
public interface CatalogLookupService {

    /** Tra cứu thông tin SKU theo variantId */
    Optional<CatalogSkuSummaryDto> getSkuSummary(UUID variantId);

    /** Tra cứu thông tin SKU theo mã sku */
    Optional<CatalogSkuSummaryDto> getSkuSummaryBySku(String sku);

    /** Tra cứu danh sách SKU theo tập hợp variantIds */
    Map<UUID, CatalogSkuSummaryDto> getSkuSummaries(Collection<UUID> variantIds);

    /** Tra cứu thông tin combo/bundle theo bundleId */
    Optional<CatalogBundleSummaryDto> getBundleSummary(UUID bundleId);

    /** Tra cứu hàng loạt thông tin combo/bundle theo tập hợp bundleIds */
    default Map<UUID, CatalogBundleSummaryDto> getBundleSummaries(Collection<UUID> bundleIds) {
        if (bundleIds == null || bundleIds.isEmpty()) return Map.of();
        Map<UUID, CatalogBundleSummaryDto> map = new java.util.LinkedHashMap<>();
        for (UUID bundleId : bundleIds) {
            if (bundleId != null) {
                getBundleSummary(bundleId).ifPresent(dto -> map.put(bundleId, dto));
            }
        }
        return map;
    }

    /** Tra cứu danh sách SKU active thuộc tập hợp productIds */
    List<CatalogSkuSummaryDto> getSkusByProductIds(Collection<UUID> productIds);

    /** Lấy ID của mọi SKU thuộc một sản phẩm gốc, kể cả SKU đã xóa mềm. */
    List<UUID> getVariantIdsByProductId(UUID productId);

    /** Lấy toàn bộ SKU đang ở trạng thái ACTIVE */
    List<CatalogSkuSummaryDto> getAllActiveSkus();

    /** Resolve a brand for cross-module validation/display. */
    Optional<CatalogBrandSummaryDto> getBrandSummary(UUID brandId);

    /** Resolve a category for cross-module validation/display. */
    Optional<CatalogCategorySummaryDto> getCategorySummary(UUID categoryId);

    /** Active reference data used by policy-editing screens. */
    List<CatalogBrandSummaryDto> getActiveBrands();

    /** Active reference data used by policy-editing screens. */
    List<CatalogCategorySummaryDto> getActiveCategories();

    /** SKU variants that currently reference the supplied warranty-policy ID. */
    List<CatalogVariantWarrantyReferenceDto> getVariantsByWarrantyPolicyId(UUID warrantyPolicyId);
}
